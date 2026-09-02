package com.highscale.api.adapter.outbound.persistence

import com.highscale.api.domain.model.Produto
import com.highscale.api.domain.port.ProdutoRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID

@Repository
class ProdutoR2dbcAdapter(
	private val databaseClient: DatabaseClient,
) : ProdutoRepository {

	override suspend fun findById(id: UUID): Produto? {
		return databaseClient
			.sql("SELECT id, nome, preco FROM produto WHERE id = :id")
			.bind("id", id)
			.map { row, _ ->
				Produto(
					id = row.get("id", UUID::class.java)!!,
					nome = row.get("nome", String::class.java)!!,
					preco = row.get("preco", BigDecimal::class.java)!!,
				)
			}
			.one()
			.awaitSingleOrNull()
	}
}
