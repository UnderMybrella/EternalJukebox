package dev.eternalbox.webserver

import dev.eternalbox.analysis.AnalysisApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.reactor.mono
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicLong

@SpringBootApplication(scanBasePackages = ["dev.eternalbox"])
@RestController
class EternalboxApplication {
	@Autowired
	lateinit var analysisApi: AnalysisApi
//	private val counter = AtomicLong(0)
//
//	@GetMapping("/hello")
//	fun hello(@RequestParam(value = "name", defaultValue = "World") name: String) =
//			Mono.just(Greeting(counter.incrementAndGet(), "Hello, $name!"))

	@GetMapping("/analysis")
	fun analysis(): Mono<String> =
			mono { analysisApi.test() }.map { analysisApi::class.qualifiedName }
}

fun main(args: Array<String>) {
	runApplication<EternalboxApplication>(*args)
}