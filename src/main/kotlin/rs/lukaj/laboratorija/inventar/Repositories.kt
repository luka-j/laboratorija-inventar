package rs.lukaj.laboratorija.inventar

import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
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
    fun findAllByDateGreaterThanAndDateLessThanEqualAndItemEquals(date: LocalDate, until: LocalDate, item: InventoryItem) : List<Change>
    fun findAllByAmountLessThanAndDateGreaterThanEqualAndDateLessThanEqual(amount: Double, date: LocalDate, until: LocalDate) : List<Change>
    fun findAllByDateGreaterThanEqualAndDateLessThanEqual(date: LocalDate, until: LocalDate) : List<Change>
    fun findAllByAmountGreaterThanAndDateGreaterThanEqualAndDateLessThanEqual(amount: Double, date: LocalDate, until: LocalDate) : List<Change>
}