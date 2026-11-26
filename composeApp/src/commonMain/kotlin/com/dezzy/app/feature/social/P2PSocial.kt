/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.feature.social.crypto.Bech32
import com.dezzy.app.feature.social.crypto.NostrCrypto
import com.dezzy.app.feature.social.crypto.toHex
import com.dezzy.app.feature.social.data.P2PPrivatePostRow
import com.dezzy.app.feature.social.data.P2PProfileRow
import com.dezzy.app.feature.social.data.P2PStore
import com.dezzy.app.feature.social.nostr.Kinds
import com.dezzy.app.feature.social.nostr.NostrEvent
import com.dezzy.app.feature.social.nostr.RelayPool
import com.dezzy.app.feature.social.nostr.SEARCH_RELAY
import com.dezzy.app.feature.social.nostr.nostrFilter
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Red social de Dezzy sin servidores propios (protocolo abierto Nostr).
 *
 * - Perfil, seguidos, publicaciones, me gusta y comentarios son eventos firmados en el dispositivo.
 * - Las cuentas privadas no publican en abierto: cada publicación viaja cifrada (gift wrap) sólo
 *   a los seguidores que la cuenta aprobó.
 * - Todo lo de Dezzy lleva la etiqueta `#dezzy` para que el feed sea sólo de música.
 *
 * Llama a [start] una vez al arrancar la app: carga la identidad y, si hay cuenta (o cuando se crea
 * o recupera una), abre los relés, el buzón y la lista de seguidos.
 */
class P2PSocial(
    private val identity: P2PIdentity,
    private val relays: RelayPool,
    private val store: P2PStore,
    private val settings: P2PSettings,
    private val http: HttpClient,
    private val crypto: NostrCrypto,
    private val imageTools: P2PImageTools,
    private val notifications: P2PNotifications,
    private val inboxWorker: P2PInboxWorker,
    private val messaging: () -> P2PMessaging,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    /** Mi clave pública en hex, o null si no hay cuenta. */
    val me: String? get() = identity.state.value.pubkeyHex

    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following: StateFlow<Set<String>> = _following.asStateFlow()
    private var lastContactsEvent: NostrEvent? = null

    private val _profiles = MutableStateFlow<Map<String, P2PUser>>(emptyMap())

    /** Perfiles conocidos (la UI los observa para pintar nombres y fotos). */
    val profiles: StateFlow<Map<String, P2PUser>> = _profiles.asStateFlow()

    /** Mis reacciones públicas por publicación (id del evento), para poder quitar el me gusta. */
    private val myReactions = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Quién puede ver lo que escucho. */
    val listeningVisibility: StateFlow<ListeningVisibility> =
        settings.listeningVisibility.stateIn(scope, SharingStarted.Eagerly, ListeningVisibility.EVERYONE)

    /** Si me anuncio en el radar «Cerca». */
    val nearbyVisible: StateFlow<Boolean> =
        settings.nearbyVisible.stateIn(scope, SharingStarted.Eagerly, true)

    fun setListeningVisibility(value: ListeningVisibility) {
        scope.launch { settings.setListeningVisibility(value) }
    }

    fun setNearbyVisible(value: Boolean) {
        scope.launch { settings.setNearbyVisible(value) }
    }

    // --- Arranque ---------------------------------------------------------------------------

    private var started = false
    private var signedInAs: String? = null

    /** Idempotente. Carga la identidad y arranca todo cuando hay cuenta. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            identity.load()
            identity.state.collect { s ->
                val key = if (s.ready) s.pubkeyHex else null
                if (key != null && key != signedInAs) {
                    signedInAs = key
                    onSignedIn()
                } else if (key == null) {
                    signedInAs = null
                }
            }
        }
    }

    private fun onSignedIn() {
        relays.warmUp()
        scope.launch { runCatching { refreshFollowing() } }
        scope.launch { me?.let { runCatching { loadProfiles(listOf(it), force = true) } } }
        messaging().start()
        inboxWorker.start()
    }

    /** Cierra la cuenta social de este dispositivo y borra todo lo guardado. */
    suspend fun signOut() {
        messaging().stop()
        inboxWorker.stop()
        notifications.clearAll()
        store.clearAll()
        identity.signOut()
        _following.value = emptySet()
        lastContactsEvent = null
        myReactions.value = emptyMap()
    }

    // --- Perfiles ---------------------------------------------------------------------------

    fun user(pubkey: String): P2PUser = _profiles.value[pubkey] ?: P2PUser(pubkey)

    val myProfile: P2PUser? get() = me?.let { user(it) }

    private fun remember(user: P2PUser) {
        _profiles.update { it + (user.pubkey to user) }
    }

    private fun parseProfile(event: NostrEvent): P2PUser? = runCatching {
        val o = json.parseToJsonElement(event.content).jsonObject
        fun str(key: String) = o[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        P2PUser(
            pubkey = event.pubkey,
            name = str("display_name") ?: str("name") ?: "",
            about = str("about") ?: "",
            picture = str("picture"),
            isPrivate = o[PRIVATE_MARK]?.jsonPrimitive?.booleanOrNull ?: false,
            // `banner` es el campo estándar de Nostr (URL); los degradados de Dezzy van aparte.
            banner = str("banner")?.takeIf { it.startsWith("https://") }
                ?: str(BANNER_PRESET_MARK)?.takeIf { it.length <= 24 }?.let { "$BANNER_PRESET_PREFIX$it" },
            bannerVideo = str(BANNER_VIDEO_MARK)?.takeIf { it.startsWith("https://") },
        )
    }.getOrNull()

    /** Carga perfiles: primero de la copia local, luego de los relés los que falten (o todos si [force]). */
    suspend fun loadProfiles(pubkeys: Collection<String>, force: Boolean = false) = withContext(Dispatchers.Default) {
        val wanted = pubkeys.filter { it.length == 64 }.distinct()
        if (wanted.isEmpty()) return@withContext
        val missingFromMemory = wanted.filter { !_profiles.value.containsKey(it) }
        if (missingFromMemory.isNotEmpty()) {
            store.profiles(missingFromMemory).forEach {
                remember(P2PUser(it.pubkey, it.name, it.about, it.picture, it.isPrivate, it.banner, it.bannerVideo))
            }
        }
        val toFetch = if (force) wanted else wanted.filter { !_profiles.value.containsKey(it) }
        if (toFetch.isEmpty()) return@withContext
        toFetch.chunked(150).forEach { chunk ->
            val events = relays.query(listOf(nostrFilter(authors = chunk, kinds = listOf(Kinds.METADATA))))
            events.groupBy { it.pubkey }.forEach { (_, list) ->
                val latest = list.maxBy { it.createdAt }
                val profile = parseProfile(latest) ?: return@forEach
                remember(profile)
                store.upsertProfile(
                    P2PProfileRow(
                        profile.pubkey, profile.name, profile.about, profile.picture, profile.isPrivate,
                        latest.createdAt, profile.banner, profile.bannerVideo,
                    ),
                )
            }
        }
    }

    /**
     * Publica mi perfil. [picture] puede ser una URL o un `data:` con una miniatura.
     * [banner] es una URL `https://` o `preset:<id>`; por defecto se conserva la actual.
     */
    suspend fun updateMyProfile(
        name: String,
        about: String,
        picture: String?,
        isPrivate: Boolean,
        banner: String? = myProfile?.banner,
        // Se conserva el video puesto desde el escritorio salvo que se cambie la portada.
        bannerVideo: String? = myProfile?.bannerVideo,
    ): Boolean = withContext(Dispatchers.Default) {
        if (identity.currentKeys() == null) return@withContext false
        val content = buildJsonObject {
            put("name", name.trim())
            put("display_name", name.trim())
            put("about", about.trim())
            picture?.let { put("picture", it) }
            put(PRIVATE_MARK, isPrivate)
            when {
                banner == null -> Unit
                banner.startsWith(BANNER_PRESET_PREFIX) -> put(BANNER_PRESET_MARK, banner.removePrefix(BANNER_PRESET_PREFIX))
                banner.startsWith("https://") -> put("banner", banner)
            }
            bannerVideo?.takeIf { it.startsWith("https://") }?.let { put(BANNER_VIDEO_MARK, it) }
        }.toString()
        val event = identity.sign(Kinds.METADATA, content, listOf(listOf("t", APP_TAG)))
        val profile = P2PUser(event.pubkey, name.trim(), about.trim(), picture, isPrivate, banner, bannerVideo)
        remember(profile)
        store.upsertProfile(
            P2PProfileRow(profile.pubkey, profile.name, profile.about, profile.picture, isPrivate, event.createdAt, banner, bannerVideo),
        )
        relays.publish(event) > 0
    }

    // --- Imágenes (las hace cada plataforma) ------------------------------------------------

    /** Miniatura cuadrada como `data:` (para el avatar). [source] es la URI/ruta del archivo elegido. */
    suspend fun avatarDataUri(source: String, size: Int = 192): String? = imageTools.avatarDataUri(source, size)

    suspend fun avatarDataUri(bytes: ByteArray, size: Int = 192): String? = imageTools.avatarDataUri(bytes, size)

    /** Reduce una foto para publicarla (lado mayor [maxSide] px, JPEG). */
    suspend fun compressPhoto(source: String, maxSide: Int = 1080): ByteArray? = imageTools.compressPhoto(source, maxSide)

    suspend fun compressPhoto(bytes: ByteArray, maxSide: Int = 1080): ByteArray? = imageTools.compressPhoto(bytes, maxSide)

    /** Video de portada (hasta 15 s y 30 MB). Falla con el motivo en español; sólo en el móvil. */
    suspend fun prepareBannerVideo(source: String): Result<P2PBannerVideo> = imageTools.prepareBannerVideo(source)

    /** Sube una foto a un servidor Blossom gratuito (autorizado con mi firma). Devuelve la URL o null. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadPhoto(bytes: ByteArray, mime: String = "image/jpeg"): String? = withContext(Dispatchers.Default) {
        if (identity.currentKeys() == null) return@withContext null
        val hash = crypto.sha256(bytes).toHex()
        val auth = identity.sign(
            Kinds.BLOSSOM_AUTH,
            "Foto de Dezzy",
            listOf(
                listOf("t", "upload"),
                listOf("x", hash),
                listOf("expiration", (NostrEvent.now() + 600).toString()),
            ),
        )
        val header = "Nostr " + Base64.encode(auth.toJson().encodeToByteArray())
        for (server in BLOSSOM_SERVERS) {
            val url = try {
                val response = http.put("$server/upload") {
                    timeout { requestTimeoutMillis = 60_000 }
                    header("Authorization", header)
                    contentType(ContentType.parse(mime))
                    setBody(bytes)
                }
                if (response.status.isSuccess()) {
                    json.parseToJsonElement(response.bodyAsText()).jsonObject["url"]?.jsonPrimitive?.contentOrNull
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (url != null) return@withContext url
        }
        null
    }

    /** Busca gente por nombre (relé con búsqueda) o por su enlace/`npub`. */
    suspend fun searchUsers(query: String): List<P2PUser> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        parsePublicKey(q)?.let { pubkey ->
            loadProfiles(listOf(pubkey))
            return@withContext listOf(user(pubkey))
        }
        val events = relays.query(
            listOf(nostrFilter(kinds = listOf(Kinds.METADATA), search = q, limit = 25)),
            relays = listOf(SEARCH_RELAY),
        )
        events.mapNotNull { event -> parseProfile(event)?.also { remember(it) } }
            .distinctBy { it.pubkey }
            .filter { it.pubkey != me }
    }

    /** Acepta `npub1…`, `nostr:npub1…`, hex o enlaces con `?npub=…` (`https://dezzy.space/?npub=…`). */
    fun parsePublicKey(input: String): String? {
        val trimmed = input.trim()
        val fromLink = Regex("""[?&]npub=([^&\s#]+)""").find(trimmed)?.groupValues?.get(1)
        return Bech32.parsePublicKey(fromLink ?: trimmed)
    }

    /** Enlace para que otra persona me añada. */
    fun myInviteLink(): String? = me?.let { "https://dezzy.space/?npub=${Bech32.npub(it)}" }

    // --- Seguir -----------------------------------------------------------------------------

    suspend fun refreshFollowing() = withContext(Dispatchers.Default) {
        val myKey = me ?: return@withContext
        val latest = relays.query(listOf(nostrFilter(authors = listOf(myKey), kinds = listOf(Kinds.CONTACTS), limit = 1)))
            .maxByOrNull { it.createdAt } ?: return@withContext
        if ((lastContactsEvent?.createdAt ?: 0) > latest.createdAt) return@withContext
        lastContactsEvent = latest
        _following.value = latest.tagValues("p").filter { it.length == 64 }.toSet()
    }

    private suspend fun publishFollowing(newSet: Set<String>): Boolean {
        if (identity.currentKeys() == null) return false
        val previous = lastContactsEvent
        val otherTags = previous?.tags?.filter { it.firstOrNull() != "p" }.orEmpty()
        val tags = otherTags + newSet.map { listOf("p", it) }
        val event = identity.sign(Kinds.CONTACTS, previous?.content.orEmpty(), tags)
        lastContactsEvent = event
        _following.value = newSet
        return relays.publish(event) > 0
    }

    /**
     * Sigue o deja de seguir. Si la cuenta es privada, además le llega una solicitud cifrada
     * y sus publicaciones sólo aparecen cuando la apruebe.
     */
    suspend fun setFollowing(pubkey: String, follow: Boolean): Boolean = withContext(Dispatchers.Default) {
        if (pubkey == me) return@withContext false
        // Asegura no pisar la lista publicada desde otro dispositivo.
        if (lastContactsEvent == null) runCatching { refreshFollowing() }
        val current = _following.value
        val ok = publishFollowing(if (follow) current + pubkey else current - pubkey)
        if (follow && user(pubkey).isPrivate) messaging().requestFollow(pubkey)
        ok
    }

    suspend fun followState(pubkey: String): FollowState {
        if (pubkey !in _following.value) return FollowState.NONE
        if (!user(pubkey).isPrivate) return FollowState.FOLLOWING
        val request = store.followRequest(pubkey, incoming = false)
        return if (request?.status == "approved") FollowState.FOLLOWING else FollowState.REQUESTED
    }

    /** ¿Puedo ver las publicaciones de esta persona? */
    suspend fun canSee(pubkey: String): Boolean =
        pubkey == me || !user(pubkey).isPrivate || followState(pubkey) == FollowState.FOLLOWING

    /** Recuentos aproximados (lo que devuelvan los relés): seguidores y seguidos. */
    suspend fun followCounts(pubkey: String): Pair<Int, Int> = withContext(Dispatchers.Default) {
        coroutineScope {
            val followers = async {
                relays.query(
                    listOf(nostrFilter(kinds = listOf(Kinds.CONTACTS), tags = mapOf("#p" to listOf(pubkey)), limit = 500)),
                ).map { it.pubkey }.distinct().size
            }
            val followingCount = async {
                relays.query(listOf(nostrFilter(authors = listOf(pubkey), kinds = listOf(Kinds.CONTACTS), limit = 1)))
                    .maxByOrNull { it.createdAt }?.tagValues("p")?.size ?: 0
            }
            followers.await() to followingCount.await()
        }
    }

    /** Solicitudes de seguimiento que me han hecho (cuenta privada) y siguen pendientes. */
    val pendingFollowRequests: Flow<List<P2PFollowRequest>> get() = store.pendingIncomingRequests()

    // --- Publicaciones ----------------------------------------------------------------------

    enum class FeedMode { FOLLOWING, DISCOVER }

    private fun NostrEvent.isReply(): Boolean = tags.any { it.firstOrNull() == "e" }

    private fun NostrEvent.toPost(isPrivate: Boolean = false): P2PPost = P2PPost(
        id = id,
        author = pubkey,
        createdAt = createdAt,
        text = content,
        song = P2PSong.fromTag(fullTag(P2PSong.TAG)),
        imageUrl = tag("image"),
        isPrivate = isPrivate,
    )

    private suspend fun privatePostsFrom(authors: List<String>, until: Long?, limit: Int): List<P2PPost> {
        if (authors.isEmpty()) return emptyList()
        return store.privatePosts(authors, limit * 2)
            .filter { until == null || it.createdAt <= until }
            .mapNotNull { NostrEvent.fromJson(it.json)?.toPost(isPrivate = true) }
            .take(limit)
    }

    /** Feed: «Siguiendo» (gente que sigo + yo) o «Descubrir» (todo #dezzy). Paginado con [until]. */
    suspend fun feed(mode: FeedMode, until: Long? = null, limit: Int = 30): List<P2PPost> = withContext(Dispatchers.Default) {
        val myKey = me
        val posts = when (mode) {
            FeedMode.FOLLOWING -> {
                val authors = (_following.value + listOfNotNull(myKey)).toList()
                if (authors.isEmpty()) return@withContext emptyList()
                val public = authors.chunked(250).flatMap { chunk ->
                    relays.query(
                        listOf(
                            nostrFilter(
                                authors = chunk,
                                kinds = listOf(Kinds.TEXT_NOTE),
                                tags = mapOf("#t" to listOf(APP_TAG)),
                                until = until,
                                limit = limit,
                            ),
                        ),
                    )
                }.filterNot { it.isReply() }.map { it.toPost() }
                public + privatePostsFrom(authors, until, limit)
            }
            FeedMode.DISCOVER -> relays.query(
                listOf(
                    nostrFilter(kinds = listOf(Kinds.TEXT_NOTE), tags = mapOf("#t" to listOf(APP_TAG)), until = until, limit = limit),
                ),
            ).filterNot { it.isReply() }.map { it.toPost() }
        }
        val sorted = posts.distinctBy { it.id }.sortedByDescending { it.createdAt }.take(limit)
        loadProfiles(sorted.map { it.author })
        withStats(sorted)
    }

    /** Publicaciones de una persona (las privadas sólo si me aprobó). */
    suspend fun userPosts(pubkey: String, until: Long? = null, limit: Int = 30): List<P2PPost> = withContext(Dispatchers.Default) {
        val private = privatePostsFrom(listOf(pubkey), until, limit)
        val public = if (user(pubkey).isPrivate && pubkey != me) {
            emptyList()
        } else {
            relays.query(
                listOf(
                    nostrFilter(
                        authors = listOf(pubkey),
                        kinds = listOf(Kinds.TEXT_NOTE),
                        tags = mapOf("#t" to listOf(APP_TAG)),
                        until = until,
                        limit = limit,
                    ),
                ),
            ).filterNot { it.isReply() }.map { it.toPost() }
        }
        withStats((public + private).distinctBy { it.id }.sortedByDescending { it.createdAt }.take(limit))
    }

    /** Añade me gusta y comentarios contados en los relés (y en la copia local si son privados). */
    suspend fun withStats(posts: List<P2PPost>): List<P2PPost> = withContext(Dispatchers.Default) {
        if (posts.isEmpty()) return@withContext posts
        val myKey = me
        val publicIds = posts.filterNot { it.isPrivate }.map { it.id }
        val comments = HashMap<String, Int>()
        // (publicación, autor) → (fecha, emoji o null, id del evento). Cuenta sólo la última de cada
        // persona: al cambiar de emoji la anterior se borra, pero no todos los relés lo respetan.
        val latest = HashMap<Pair<String, String>, Triple<Long, String?, String>>()
        fun offer(postId: String, author: String, createdAt: Long, emoji: String?, eventId: String) {
            val key = postId to author
            val current = latest[key]
            if (current == null || createdAt > current.first || (createdAt == current.first && eventId > current.third)) {
                latest[key] = Triple(createdAt, emoji, eventId)
            }
        }
        if (publicIds.isNotEmpty()) {
            val events = coroutineScope {
                publicIds.chunked(100).map { chunk ->
                    async {
                        relays.query(
                            listOf(
                                nostrFilter(kinds = listOf(Kinds.REACTION, Kinds.TEXT_NOTE), tags = mapOf("#e" to chunk), limit = 1000),
                            ),
                            timeoutMs = 5000,
                        )
                    }
                }.awaitAll().flatten()
            }
            for (event in events) {
                val target = event.tagValues("e").lastOrNull { it in publicIds } ?: continue
                when (event.kind) {
                    Kinds.REACTION -> offer(target, event.pubkey, event.createdAt, reactionEmoji(event.content), event.id)
                    Kinds.TEXT_NOTE -> comments[target] = (comments[target] ?: 0) + 1
                }
            }
        }
        val privateIds = posts.filter { it.isPrivate }.map { it.id }
        if (privateIds.isNotEmpty()) {
            store.privateReactions(privateIds).forEach { r ->
                if (r.type == "like") {
                    offer(r.postId, r.author, r.createdAt, reactionEmoji(r.content), r.id)
                } else {
                    comments[r.postId] = (comments[r.postId] ?: 0) + 1
                }
            }
        }
        val reactions = HashMap<String, HashMap<String, Int>>()
        val mine = HashMap<String, String>()
        val mineEvents = HashMap<String, String>()
        for ((key, value) in latest) {
            val (postId, author) = key
            val emoji = value.second ?: continue
            val counts = reactions.getOrPut(postId) { HashMap() }
            counts[emoji] = (counts[emoji] ?: 0) + 1
            if (author == myKey) {
                mine[postId] = emoji
                if (postId in publicIds) mineEvents[postId] = value.third
            }
        }
        if (mineEvents.isNotEmpty()) myReactions.update { it + mineEvents }
        posts.map { p ->
            val counts = reactions[p.id].orEmpty()
            p.copy(
                likes = counts.values.sum(),
                comments = comments[p.id] ?: 0,
                likedByMe = p.id in mine,
                reactions = counts,
                myReaction = mine[p.id],
            )
        }
    }

    /**
     * Publica. Si mi cuenta es privada, la publicación no sale en abierto: se cifra para cada
     * seguidor aprobado (y para mí, para tenerla en otros dispositivos).
     */
    suspend fun createPost(text: String, song: P2PSong?, photo: ByteArray?): Boolean = withContext(Dispatchers.Default) {
        val k = identity.currentKeys() ?: return@withContext false
        val imageUrl = photo?.let { uploadPhoto(it) ?: return@withContext false }
        val tags = buildList {
            add(listOf("t", APP_TAG))
            song?.let {
                add(it.toTag())
                add(listOf("r", it.url))
            }
            imageUrl?.let {
                add(listOf("image", it))
                add(listOf("imeta", "url $it", "m image/jpeg"))
            }
        }
        if (myProfile?.isPrivate == true) {
            val rumor = NostrEvent.rumor(k.publicKey, Kinds.TEXT_NOTE, tags + listOf(listOf(APP_TAG, "private_post")), text)
            store.insertPrivatePost(P2PPrivatePostRow(rumor.id, k.publicKey, rumor.createdAt, rumor.toJson(includeSig = false)))
            val audience = store.approvedFollowers() + k.publicKey
            messaging().sendWrapped(rumor, audience)
            true
        } else {
            relays.publish(identity.sign(Kinds.TEXT_NOTE, text, tags)) > 0
        }
    }

    /** Me gusta / quitar me gusta (❤️). Devuelve si queda con reacción. */
    suspend fun toggleLike(post: P2PPost): Boolean =
        react(post, if (post.myReaction != null) null else DEFAULT_REACTION) != null

    /**
     * Reacciona con [emoji] (uno de [REACTIONS]) o quita la reacción con null (NIP-25).
     * Devuelve la reacción que queda publicada.
     */
    suspend fun react(post: P2PPost, emoji: String?): String? = withContext(Dispatchers.Default) {
        val k = identity.currentKeys() ?: return@withContext post.myReaction
        val wanted = emoji?.takeIf { it in REACTIONS }
        if (wanted == post.myReaction) return@withContext wanted
        // ❤️ sale como «+», que todos los clientes de Nostr entienden como me gusta.
        val content = when (wanted) {
            null -> "-"
            DEFAULT_REACTION -> "+"
            else -> wanted
        }
        if (post.isPrivate) {
            // Privadas: no hay borrado en los relés; cuenta la última de cada persona («-» la retira).
            val rumor = NostrEvent.rumor(
                k.publicKey,
                Kinds.REACTION,
                listOf(listOf("e", post.id), listOf("p", post.author), listOf(APP_TAG, "private_reaction")),
                content,
            )
            messaging().storePrivateReaction(rumor)
            messaging().sendWrapped(rumor, listOf(post.author, k.publicKey).distinct())
            return@withContext wanted
        }
        myReactions.value[post.id]?.let { old ->
            myReactions.update { it - post.id }
            relays.publish(identity.sign(Kinds.DELETION, "", listOf(listOf("e", old), listOf("k", "7"))))
        }
        if (wanted == null) return@withContext null
        val reaction = identity.sign(
            Kinds.REACTION,
            content,
            listOf(listOf("e", post.id), listOf("p", post.author), listOf("k", "1")),
        )
        if (relays.publish(reaction) > 0) {
            myReactions.update { it + (post.id to reaction.id) }
            wanted
        } else {
            null
        }
    }

    suspend fun comments(post: P2PPost): List<P2PComment> = withContext(Dispatchers.Default) {
        val list = if (post.isPrivate) {
            store.privateReactions(listOf(post.id)).filter { it.type == "comment" }
                .map { P2PComment(it.id, it.author, it.createdAt, it.content, it.replyTo) }
        } else {
            relays.query(
                listOf(nostrFilter(kinds = listOf(Kinds.TEXT_NOTE), tags = mapOf("#e" to listOf(post.id)), limit = 300)),
            ).map { event ->
                // NIP-10: ["e", raíz, "", "root"] y ["e", padre, "", "reply"].
                val parent = event.tags.firstOrNull { it.size >= 4 && it[0] == "e" && it[3] == "reply" }?.get(1)
                P2PComment(event.id, event.pubkey, event.createdAt, event.content, parent?.takeIf { it != post.id })
            }
        }
        loadProfiles(list.map { it.author })
        threadComments(list)
    }

    /**
     * Ordena en hilos de un nivel: cada comentario seguido de sus respuestas. Una respuesta
     * a otra respuesta cuelga del comentario de primer nivel; si el padre no está, va suelta.
     */
    fun threadComments(list: List<P2PComment>): List<P2PComment> {
        val byId = list.associateBy { it.id }
        fun rootOf(c: P2PComment): String? {
            var parent = c.replyTo?.let { byId[it] } ?: return null
            repeat(8) { parent = parent.replyTo?.let { byId[it] } ?: return parent.id }
            return parent.id
        }
        val resolved = list.distinctBy { it.id }.map { it.copy(replyTo = rootOf(it)) }
        val replies = resolved.filter { it.replyTo != null }.groupBy { it.replyTo }
        return resolved.filter { it.replyTo == null }.sortedBy { it.createdAt }.flatMap { top ->
            listOf(top) + replies[top.id].orEmpty().sortedBy { it.createdAt }
        }
    }

    suspend fun addComment(post: P2PPost, text: String, replyTo: P2PComment? = null): P2PComment? = withContext(Dispatchers.Default) {
        val k = identity.currentKeys() ?: return@withContext null
        val clean = text.trim().takeIf { it.isNotEmpty() } ?: return@withContext null
        val tags = buildList {
            add(listOf("e", post.id, "", "root"))
            replyTo?.let { add(listOf("e", it.id, "", "reply")) }
            add(listOf("p", post.author))
            replyTo?.author?.takeIf { it != post.author }?.let { add(listOf("p", it)) }
            add(listOf("t", APP_TAG))
        }
        // Sólo un nivel: responder a una respuesta cuelga del mismo comentario de arriba.
        val thread = replyTo?.let { it.replyTo ?: it.id }
        if (post.isPrivate) {
            val rumor = NostrEvent.rumor(k.publicKey, Kinds.TEXT_NOTE, tags + listOf(listOf(APP_TAG, "private_comment")), clean)
            messaging().storePrivateReaction(rumor)
            messaging().sendWrapped(rumor, listOf(post.author, k.publicKey).distinct())
            P2PComment(rumor.id, k.publicKey, rumor.createdAt, clean, thread)
        } else {
            val event = identity.sign(Kinds.TEXT_NOTE, clean, tags)
            if (relays.publish(event) > 0) P2PComment(event.id, k.publicKey, event.createdAt, clean, thread) else null
        }
    }

    suspend fun deletePost(post: P2PPost): Boolean = withContext(Dispatchers.Default) {
        val k = identity.currentKeys() ?: return@withContext false
        if (post.author != k.publicKey) return@withContext false
        if (post.isPrivate) {
            store.deletePrivatePost(post.id)
            val rumor = NostrEvent.rumor(k.publicKey, Kinds.DELETION, listOf(listOf("e", post.id), listOf(APP_TAG, "private_delete")), "")
            messaging().sendWrapped(rumor, store.approvedFollowers() + k.publicKey)
            true
        } else {
            relays.publish(identity.sign(Kinds.DELETION, "", listOf(listOf("e", post.id), listOf("k", "1")))) > 0
        }
    }

    /** Reenvía mis últimas publicaciones privadas a un seguidor recién aprobado. */
    internal suspend fun shareRecentPrivatePosts(follower: String) {
        val myKey = me ?: return
        store.privatePostsOf(myKey, 20).mapNotNull { NostrEvent.fromJson(it.json) }.forEach { rumor ->
            messaging().sendWrapped(rumor, listOf(follower))
        }
    }

    /** Usado por la mensajería al recibir una publicación privada. */
    internal suspend fun storePrivatePost(rumor: NostrEvent) {
        store.insertPrivatePost(P2PPrivatePostRow(rumor.id, rumor.pubkey, rumor.createdAt, rumor.toJson(includeSig = false)))
    }

    companion object {
        const val APP_TAG = "dezzy"
        private const val PRIVATE_MARK = "dezzy_private"

        /** Degradado de portada de Dezzy (sólo el nombre del preset; no sube nada). */
        const val BANNER_PRESET_MARK = "dezzy_banner"
        const val BANNER_PRESET_PREFIX = "preset:"
        const val BANNER_VIDEO_MARK = "dezzy_banner_video"

        /** Reacciones de Dezzy (NIP-25). El «+» de otros clientes de Nostr cuenta como ❤️. */
        val REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
        const val DEFAULT_REACTION = "❤️"

        /** Servidores Blossom gratuitos para fotos de publicaciones (las fotos son públicas). */
        private val BLOSSOM_SERVERS = listOf("https://blossom.primal.net", "https://nostr.download")

        /** Emoji de una reacción; null si la retira («-») o no es nuestra. */
        private fun reactionEmoji(content: String): String? = when {
            content == "-" -> null
            content in REACTIONS -> content
            else -> DEFAULT_REACTION
        }
    }
}
