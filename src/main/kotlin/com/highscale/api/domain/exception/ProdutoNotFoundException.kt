package com.highscale.api.domain.exception

import java.util.UUID

class ProdutoNotFoundException(id: UUID) : RuntimeException("Produto não encontrado: $id")
