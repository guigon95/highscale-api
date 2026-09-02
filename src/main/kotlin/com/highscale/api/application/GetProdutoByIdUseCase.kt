package com.highscale.api.application

import com.highscale.api.domain.exception.ProdutoNotFoundException
import com.highscale.api.domain.model.Produto
import com.highscale.api.domain.port.ProdutoRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetProdutoByIdUseCase(
	private val produtoRepository: ProdutoRepository,
) {

	suspend fun execute(id: UUID): Produto {
		return produtoRepository.findById(id) ?: throw ProdutoNotFoundException(id)
	}
}
