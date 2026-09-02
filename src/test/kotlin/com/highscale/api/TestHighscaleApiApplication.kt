package com.highscale.api

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<HighscaleApiApplication>().with(TestcontainersConfiguration::class).run(*args)
}
