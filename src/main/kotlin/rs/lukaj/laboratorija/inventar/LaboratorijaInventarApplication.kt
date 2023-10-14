package rs.lukaj.laboratorija.inventar

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class LaboratorijaInventarApplication

fun main(args: Array<String>) {
    runApplication<LaboratorijaInventarApplication>(*args)
}
