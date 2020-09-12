package rs.bolnicapancevo.laboratorija.inventar

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.IllegalStateException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs

@Service
class CrudService(@Autowired val inventoryRepository: InventoryRepository,
                  @Autowired val itemRepository: ItemRepository,
                  @Autowired val inventoryItemRepository: InventoryItemRepository,
                  @Autowired val changeRepository: ChangeRepository) {

    companion object {
        val LOGGER = LoggerFactory.getLogger(CrudService::class.java)
    }

    fun addInventory(name: String): Inventory {
        val inv = Inventory(-1, name, ArrayList(0), 0)
        return inventoryRepository.save(inv)
    }

    fun addItem(item: Item) : Item {
        return itemRepository.save(item)
    }

    fun addItemToRepository(invName: String, id1: Int, id2: Int, kolicina: Double) : InventoryItem {
        return inventoryRepository.findByIme(invName).flatMap { inv ->
            itemRepository.findByBrPartijeAndBrStavke(id1, id2).map { item ->
                val inventoryItem = InventoryItem(-1, inv, item, kolicina, ArrayList())
                inventoryItemRepository.save(inventoryItem)
            }
        }.orElseThrow { NotFoundException("Not found!") }
    }

    fun isItemAvailable(invId: Int, brPartije: Int, brStavke: Int, kolicina: Double) : Boolean {
        return inventoryRepository.findById(invId).flatMap { inv ->
            itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke).map { item ->
                val inventoryItem = inventoryItemRepository.findByInventoryAndItem(inv, item)
                inventoryItem.isPresent && inventoryItem.get().kolicina >= kolicina
            }
        }.orElseThrow { NotFoundException("Not found!") }
    }

    fun setItemAmount(item: Item, inventory: Inventory, amount: Double) : InventoryItem {
        val invItem = inventoryItemRepository.findByInventoryAndItem(inventory, item)
        var prevAmount = 0.0
        val changedInvItem: InventoryItem
        if(invItem.isEmpty) {
            LOGGER.info("Adding {} of item {} to inventory {}", amount, item.ime, inventory.ime)
            val newItem = InventoryItem(-1, inventory, item, amount, ArrayList());
            changedInvItem = inventoryItemRepository.save(newItem)
        } else {
            val ii = invItem.get()
            LOGGER.info("Changing amount of {} from {} to {} in inventory {}", item.ime, ii.kolicina, amount, inventory.ime)
            prevAmount = ii.kolicina
            ii.kolicina = amount
            changedInvItem = inventoryItemRepository.save(ii)
        }

        if(amount != prevAmount) {
            val change = Change(-1, changedInvItem, amount - prevAmount, LocalDateTime.now())
            changeRepository.save(change)
        }

        return changedInvItem
    }

    fun revertChange(id: Int) {
        val change = changeRepository.findById(id).orElseThrow { NotFoundException("Change not found!") }
        LOGGER.info("Reverting change of {} for item {} in inventory {}", change.amount, change.item.item.id,
                change.item.inventory.ime)
        change.item.kolicina -= change.amount
        inventoryItemRepository.save(change.item)
        changeRepository.deleteById(id)
    }

    @Transactional
    fun transfer(data: TransferRequest) {
        val time = LocalDateTime.of(LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(data.time)), LocalTime.now())
        if(data.from != -1) {
            val from = inventoryRepository.findById(data.from).orElseThrow { NotFoundException("Not found from inventory!") }
            for(itemData in data.items) {
                val item = itemRepository.findByBrPartijeAndBrStavke(itemData.brPartije, itemData.brStavke).orElseThrow {IllegalStateException()}
                val fromItem = inventoryItemRepository.findByInventoryAndItem(from, item).orElseThrow {IllegalStateException()}
                fromItem.kolicina -= itemData.amount
                inventoryItemRepository.save(fromItem)
                val changeFrom = Change(-1, fromItem, -itemData.amount, time)
                changeRepository.save(changeFrom)
            }
        }
        if(data.to != -1) {
            val to = inventoryRepository.findById(data.to).orElseThrow { NotFoundException("Not found to inventory!") }
            for(itemData in data.items) {
                val item = itemRepository.findByBrPartijeAndBrStavke(itemData.brPartije, itemData.brStavke).orElseThrow {IllegalStateException()}
                val toItem = inventoryItemRepository.findByInventoryAndItem(to, item).map { toItem ->
                    toItem.kolicina += itemData.amount
                    toItem
                }.orElse(InventoryItem(-1, to, item, itemData.amount, ArrayList()))
                inventoryItemRepository.save(toItem)
                val changeTo = Change(-1, toItem, itemData.amount, time)
                changeRepository.save(changeTo)
            }
        }
    }

    fun getInventory(name: String) : Inventory {
        val maybeInventory = inventoryRepository.findByIme(name)
        return maybeInventory.orElseThrow { NotFoundException("Not found!")}
    }

    fun getItem(brPartije: Int, brStavke: Int) : Item {
        return itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke).orElseThrow { NotFoundException("Not found!") }
    }

    fun getItemIfExists(brPartije: Int, brStavke: Int) = itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke)

    fun getAllItems() : List<ItemWithMappedInventory> {
        val list = ArrayList<ItemWithMappedInventory>()
        itemRepository.findAll().forEach { item -> list.add(ItemWithMappedInventory(item))}
        return list
    }

    fun getAllInventories() : List<Inventory> {
        val list = ArrayList<Inventory>()
        inventoryRepository.findAll().forEach {inv -> list.add(inv)}
        return list.sortedBy { i -> i.sortOrder }
    }

    fun getAllChangesSince(date: LocalDateTime, until: LocalDateTime, inventory: String) : List<Change> {
        val allChanges = changeRepository.findAllByDateGreaterThanEqualAndDateLessThanEqual(date, until)
        return if(inventory.isEmpty()) allChanges.sortedByDescending { change -> change.date }
        else {
            val inventories = inventory.split(",").map { inv -> inv.trim().toLowerCase() }.toHashSet()
            return allChanges.filter { change -> inventories.contains(change.item.inventory.ime.toLowerCase()) }
                    .sortedByDescending { change -> change.date }
        }
    }

    fun getAllPricesSince(date: LocalDateTime, until: LocalDateTime, inventory: String) : List<Change> {
        val purchases = getAllPurchasesAsMap(date, until, inventory)
        val aggregated = HashMap<Int, MutablePair<InventoryItem, Double>>()
        purchases.forEach { (item, amount) ->
            val p = item.item.brPartije
            if(aggregated.containsKey(p)) {
                aggregated[p]!!.second += amount*item.item.cena
            } else {
                aggregated[p] = MutablePair(item, amount*item.item.cena)
            }
        }
        return aggregated.map { e -> Change(-1, e.value.first, e.value.second, LocalDateTime.now()) }
    }

    fun getAllExpensesAsMap(date: LocalDateTime, until: LocalDateTime, inventory: String) : Map<InventoryItem, Double> {
        return aggregateChanges(changeRepository
                .findAllByAmountLessThanAndDateGreaterThanEqualAndDateLessThanEqual(0.0, date, until), inventory)
    }

    fun getAllExpensesSince(date: LocalDateTime, until: LocalDateTime, inventory: String) : List<Change> {
        return changesToList(getAllExpensesAsMap(date, until, inventory))
    }

    fun getAllPurchasesAsMap(date: LocalDateTime, until: LocalDateTime, inventory: String) : Map<InventoryItem, Double> {
        return aggregateChanges(changeRepository
                .findAllByAmountGreaterThanAndDateGreaterThanEqualAndDateLessThanEqual(0.0, date, until), inventory)
    }

    fun getAllPurchasesSince(date: LocalDateTime, until: LocalDateTime, inventory: String) : List<Change> {
        return changesToList(getAllPurchasesAsMap(date, until, inventory))
    }

    fun resetInventory(name: String) {
        val inventory = inventoryRepository.findByIme(name).orElseThrow {NotFoundException("Inventory doesn't exist!")}

        LOGGER.info("Resetting inventory {}", name)
        for(item in inventory.items) {
            item.kolicina = 0.0
            inventoryItemRepository.save(item)
        }
    }

    private fun aggregateChanges(list : List<Change>, inventory: String) : Map<InventoryItem, Double> {
        var changes = list
        val inventories = inventory.split(",").map { inv -> inv.trim().toLowerCase() }.toHashSet()
        if(inventory.isNotEmpty()) {
            changes = changes.filter { change -> inventories.contains(change.item.inventory.ime.toLowerCase()) }
        }
        val expensesMap = HashMap<InventoryItem, Double>()
        changes.forEach { change ->
            if(!expensesMap.containsKey(change.item)) {
                expensesMap[change.item] = abs(change.amount)
            } else {
                expensesMap[change.item] = expensesMap[change.item]!! + abs(change.amount)
            }
        }
        return expensesMap
    }

    private fun changesToList(map : Map<InventoryItem, Double>) : List<Change> {
        return map.map { e -> Change(-1, e.key, e.value, LocalDateTime.now()) }.sortedByDescending { c -> c.date }
    }
}

class ItemWithMappedInventory(var id : Int, var ime : String, var dobavljac : String, var brPartije: Int,
                              var brStavke: Int, var cena: Double, var amounts: HashMap<String, Double>) {
    constructor(item: Item) :  this(item.id, item.ime, item.dobavljac, item.brPartije, item.brStavke, item.cena,
            HashMap()) {
        amounts = HashMap()
        for(inv in item.inventory) {
            amounts[inv.inventory.ime] = inv.kolicina
        }
    }
}

class MutablePair<F, S>(var first: F, var second: S)