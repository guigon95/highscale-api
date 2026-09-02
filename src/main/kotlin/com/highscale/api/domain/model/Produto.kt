package com.highscale.api.domain.model

import java.math.BigDecimal
import java.util.UUID

data class Produto(
	val id: UUID,
	val nome: String,
	val preco: BigDecimal,
)
