package com.highscale.api.adapter.inbound.web

import com.highscale.api.domain.exception.ProdutoNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(ProdutoNotFoundException::class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	fun handleNotFound(ex: ProdutoNotFoundException): ErrorResponse {
		return ErrorResponse(
			status = HttpStatus.NOT_FOUND.value(),
			message = ex.message ?: "Produto não encontrado",
		)
	}
}
