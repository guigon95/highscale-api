package com.highscale.api.adapter.inbound.web

import com.highscale.api.domain.model.Produto
import java.math.BigDecimal
import java.util.UUID

data class ProdutoResponse(
	val id: UUID,
	val nome: String,
	val preco: BigDecimal,
) {
	companion object {
		fun from(produto: Produto): ProdutoResponse =
			ProdutoResponse(
				id = produto.id,
				nome = produto.nome,
				preco = produto.preco,
			)
	}
}
