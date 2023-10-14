package rs.lukaj.laboratorija.inventar

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sign

@Component
class ExpirationScheduler(@Autowired private val changeRepository: ChangeRepository,
        @Autowired private val inventoryItemRepository: InventoryItemRepository) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(initialDelay = 0, fixedDelay = 300, timeUnit = TimeUnit.SECONDS)
    @Transactional
    fun createExpirationChanges() {
        logger.info { "Running expiration scheduler" }
        val now = LocalDateTime.now()
        val expiredChanges = changeRepository.findAllByExpirationTimeIsNotNullAndExpirationTimeBefore(now)
        val groupedExpirations = expiredChanges.groupBy { c -> c.item }
        logger.info { "Found ${expiredChanges.size} expired changes for ${groupedExpirations.size} items" }

        groupedExpirations.forEach { (item, changes) ->
            val totalExpired = changes.sumOf { c -> c.amount } // ordinarily positive
            val totalSpent = item.changes .filter { c -> c.amount.sign != totalExpired.sign }.sumOf { c -> c.amount } // ordinarily negative
            if(abs(totalExpired) > abs(totalSpent)) {
                val expirationAmount = -(totalSpent + totalExpired)
                val expirationChange = Change(-1, item, expirationAmount, now, null, ChangeType.EXPIRATION)
                changeRepository.save(expirationChange)
                item.kolicina += expirationAmount
                inventoryItemRepository.save(item)
                logger.info { "Created an expiration change for amount $expirationAmount, item " +
                        "${item.item.brPartije}/${item.item.brStavke} in inventory ${item.inventory.ime}" }
            }
        }

        logger.info { "Expiration scheduler finished" }
    }
}