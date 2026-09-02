package com.highscale.api.domain.port

import com.highscale.api.domain.model.Produto
import java.util.UUID

interface ProdutoRepository {
	suspend fun findById(id: UUID): Produto?
}
