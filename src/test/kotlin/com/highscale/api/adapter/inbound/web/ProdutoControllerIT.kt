package com.highscale.api.adapter.inbound.web

import com.highscale.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@SpringBootTest
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration::class)
class ProdutoControllerIT {

	@Autowired
	lateinit var webTestClient: WebTestClient

	@Test
	fun `returns seed produto by id`() {
		webTestClient.get()
			.uri("/api/produtos/{id}", SEED_CADERNO_ID)
			.exchange()
			.expectStatus().isOk
			.expectBody()
			.jsonPath("$.id").isEqualTo(SEED_CADERNO_ID.toString())
			.jsonPath("$.nome").isEqualTo("Caderno")
			.jsonPath("$.preco").isEqualTo(12.90)
	}

	@Test
	fun `returns 404 when produto does not exist`() {
		val unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999")

		webTestClient.get()
			.uri("/api/produtos/{id}", unknownId)
			.exchange()
			.expectStatus().isNotFound
			.expectBody()
			.jsonPath("$.status").isEqualTo(404)
			.jsonPath("$.message").isEqualTo("Produto não encontrado: $unknownId")
	}

	companion object {
		private val SEED_CADERNO_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
	}
}
