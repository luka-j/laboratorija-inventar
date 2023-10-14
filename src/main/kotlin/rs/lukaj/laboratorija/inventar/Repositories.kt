package rs.lukaj.laboratorija.inventar

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime
import java.util.*


interface ItemRepository : CrudRepository<Item, Int> {
    fun findByBrPartijeAndBrStavke(brPartije: Int, brStavke: Int) : Optional<Item>
}

interface InventoryRepository : CrudRepository<Inventory, Int> {
    fun findByIme(ime: String) : Optional<Inventory>
}

interface InventoryItemRepository : CrudRepository<InventoryItem, Long> {
    fun findByInventoryAndItem(inventory: Inventory, item: Item) : Optional<InventoryItem>
}

interface ChangeRepository : CrudRepository<Change, Long> {
    fun findAllByAmountGreaterThan(amount: Double) : List<Change>
    fun findAllByAmountLessThan(amount: Double) : List<Change>
    fun findAllByDateGreaterThanEqualAndDateLessThanEqualAndItemEquals(date: LocalDateTime, until: LocalDateTime, item: InventoryItem) : List<Change>
    fun findAllByAmountLessThanAndDateGreaterThanEqualAndDateLessThanEqualAndTypeEquals(amount: Double, date: LocalDateTime, until: LocalDateTime, type: ChangeType) : List<Change>
    fun findAllByDateGreaterThanEqualAndDateLessThanEqual(date: LocalDateTime, until: LocalDateTime) : List<Change>
    fun findAllByAmountGreaterThanAndDateGreaterThanEqualAndDateLessThanEqualAndTypeEquals(amount: Double, date: LocalDateTime, until: LocalDateTime, type: ChangeType) : List<Change>

    fun findAllByExpirationTimeIsNotNullAndExpirationTimeBefore(now: LocalDateTime) : List<Change>

    @Query("SELECT sum(c.amount) from Change c where c.amount > 0 and c.item = :item and c.date >= :time")
    fun getSumOfPositiveChangesForItemSince(item: InventoryItem, time: LocalDateTime) : Double
    @Query("SELECT sum(c.amount) from Change c where c.amount < 0 and c.item = :item and c.date >= :time")
    fun getSumOfNegativeChangesForItemSince(item: InventoryItem, time: LocalDateTime) : Double
}