package com.highscale.api.adapter.inbound.web

import com.highscale.api.application.GetProdutoByIdUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/produtos")
class ProdutoController(
	private val getProdutoByIdUseCase: GetProdutoByIdUseCase,
) {

	@GetMapping("/{id}")
	suspend fun getById(@PathVariable id: UUID): ProdutoResponse {
		return ProdutoResponse.from(getProdutoByIdUseCase.execute(id))
	}
}
