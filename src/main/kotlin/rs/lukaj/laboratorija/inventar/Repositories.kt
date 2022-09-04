package rs.lukaj.laboratorija.inventar

import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
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
    fun findAllByDateGreaterThanAndDateLessThanEqualAndItemEquals(date: LocalDateTime, until: LocalDateTime, item: InventoryItem) : List<Change>
    fun findAllByAmountLessThanAndDateGreaterThanEqualAndDateLessThanEqualAndReversalEquals(amount: Double, date: LocalDateTime, until: LocalDateTime, reversal: Boolean) : List<Change>
    fun findAllByDateGreaterThanEqualAndDateLessThanEqual(date: LocalDateTime, until: LocalDateTime) : List<Change>
    fun findAllByAmountGreaterThanAndDateGreaterThanEqualAndDateLessThanEqualAndReversalEquals(amount: Double, date: LocalDateTime, until: LocalDateTime, reversal: Boolean) : List<Change>
}