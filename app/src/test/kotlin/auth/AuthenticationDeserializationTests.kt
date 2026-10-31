package org.jellyfin.androidtv.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.ByteReadChannel
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.model.api.AuthenticationResult
import java.util.UUID

class AuthenticationDeserializationTests : FunSpec({
	test("Quick Connect authentication deserializes Jellyfin 10.11 capabilities without obsolete fields") {
		// Capabilities is the unchanged 10.11.11 server sample; identities and token are test-only.
		val payload = """
			{
				"User": {
					"Id": "00000000000000000000000000000001",
					"Name": "test-user",
					"HasPassword": true,
					"HasConfiguredPassword": true,
					"HasConfiguredEasyPassword": false
				},
				"SessionInfo": {
					"UserId": "00000000000000000000000000000001",
					"LastActivityDate": "2026-10-30T00:00:00Z",
					"LastPlaybackCheckIn": "2026-10-30T00:00:00Z",
					"IsActive": true,
					"SupportsMediaControl": false,
					"SupportsRemoteControl": false,
					"HasCustomDeviceName": false,
					"Capabilities": {
						"PlayableMediaTypes": [],
						"SupportedCommands": [],
						"SupportsMediaControl": false,
						"SupportsPersistentIdentifier": true
					}
				},
				"AccessToken": "test-access-token",
				"ServerId": "00000000000000000000000000000002"
			}
		""".trimIndent()

		val response = RawResponse(
			body = ByteReadChannel(payload.toByteArray(Charsets.UTF_8)),
			status = 200,
			headers = emptyMap(),
		).createResponse<AuthenticationResult>()

		val authentication = response.content
		val user = authentication.user.shouldNotBeNull()
		user.id shouldBe UUID.fromString("00000000-0000-0000-0000-000000000001")
		user.name shouldBe "test-user"
		authentication.accessToken shouldBe "test-access-token"
		authentication.serverId shouldBe "00000000000000000000000000000002"

		val session = authentication.sessionInfo.shouldNotBeNull()
		session.userId shouldBe user.id
		val capabilities = session.capabilities.shouldNotBeNull()
		capabilities.playableMediaTypes shouldBe emptyList()
		capabilities.supportedCommands shouldBe emptyList()
		capabilities.supportsMediaControl shouldBe false
		capabilities.supportsPersistentIdentifier shouldBe true
		capabilities.supportsContentUploading shouldBe false
		capabilities.supportsSync shouldBe false
	}
})
