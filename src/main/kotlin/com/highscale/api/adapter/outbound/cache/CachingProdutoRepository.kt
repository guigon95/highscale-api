package com.highscale.api.adapter.outbound.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.highscale.api.adapter.outbound.persistence.ProdutoR2dbcAdapter
import com.highscale.api.domain.model.Produto
import com.highscale.api.domain.port.ProdutoRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
@Primary
@ConditionalOnProperty(
	prefix = "app.cache.produtos",
	name = ["enabled"],
	havingValue = "true",
	matchIfMissing = true,
)
class CachingProdutoRepository(
	private val delegate: ProdutoR2dbcAdapter,
	@Value("\${app.cache.produtos.maximum-size:10000}") maximumSize: Long,
	@Value("\${app.cache.produtos.expire-after-write:5m}") expireAfterWrite: Duration,
) : ProdutoRepository {

	private val cache: Cache<UUID, Produto> = Caffeine.newBuilder()
		.maximumSize(maximumSize)
		.expireAfterWrite(expireAfterWrite)
		.build()

	override suspend fun findById(id: UUID): Produto? {
		cache.getIfPresent(id)?.let { return it }
		return delegate.findById(id)?.also { cache.put(id, it) }
	}
}
