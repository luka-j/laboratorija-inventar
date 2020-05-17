package rs.bolnicapancevo.laboratorija.inventar

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate
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
        val inv = Inventory(-1, name, ArrayList(0))
        return inventoryRepository.save(inv)
    }

    fun addItem(item: Item) : Item {
        return itemRepository.save(item)
    }

    fun addItemToRepository(invName: String, id1: Int, id2: Int, kolicina: Double) : InventoryItem {
        val inv = inventoryRepository.findByIme(invName)
        val item = itemRepository.findByBrPartijeAndBrStavke(id1, id2)
        if(inv.isEmpty || item.isEmpty) throw NotFoundException("Not found!")
        val inventoryItem = InventoryItem(-1, inv.get(), item.get(), kolicina, ArrayList())
        return inventoryItemRepository.save(inventoryItem)
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
            val change = Change(-1, changedInvItem, amount - prevAmount, LocalDate.now())
            changeRepository.save(change)
        }

        return changedInvItem
    }

    fun revertChange(id: Int) {
        val change = changeRepository.findById(id).orElseThrow { NotFoundException("Change not found!") }
        change.item.kolicina -= change.amount
        inventoryItemRepository.save(change.item)
        changeRepository.deleteById(id)
    }

    fun getInventory(name: String) : Inventory {
        val maybeInventory = inventoryRepository.findByIme(name)
        return maybeInventory.orElseThrow { NotFoundException("Not found!")}
    }

    fun getItem(brPartije: Int, brStavke: Int) : Item {
        return itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke).orElseThrow { NotFoundException("Not found!") }
    }

    fun getAllItems() : List<ItemWithMappedInventory> {
        val list = ArrayList<ItemWithMappedInventory>()
        itemRepository.findAll().forEach { item -> list.add(ItemWithMappedInventory(item))}
        return list
    }

    fun getAllInventories() : List<Inventory> {
        val list = ArrayList<Inventory>()
        inventoryRepository.findAll().forEach {inv -> list.add(inv)}
        return list
    }

    fun getAllChangesSince(date: LocalDate, inventory: String) : List<Change> {
        val allChanges = changeRepository.findAllByDateGreaterThanEqual(date)
        return if(inventory.isEmpty()) allChanges
        else allChanges.filter { change -> change.item.inventory.ime.equals(inventory, ignoreCase = true) }
    }

    fun getAllExpensesSince(date: LocalDate, inventory: String) : List<Change> {
        val changes = changeRepository.findAllByAmountLessThanAndDateGreaterThanEqual(0.0, date)
        return aggregateChanges(changes, inventory)
    }

    fun getAllPurchasesSince(date: LocalDate, inventory: String) : List<Change> {
        val changes = changeRepository.findAllByAmountGreaterThanAndDateGreaterThanEqual(0.0, date)
        return aggregateChanges(changes, inventory)
    }

    fun resetInventory(name: String) {
        val inventory = inventoryRepository.findByIme(name).orElseThrow {NotFoundException("Inventory doesn't exist!")}

        for(item in inventory.items) {
            item.kolicina = 0.0
            inventoryItemRepository.save(item)
        }
    }

    private fun aggregateChanges(list : List<Change>, inventory: String) : List<Change> {
        var changes = list
        if(inventory.isNotEmpty()) {
            changes = changes.filter { change -> change.item.inventory.ime.equals(inventory, ignoreCase = true) }
        }
        val expensesMap = HashMap<InventoryItem, Double>()
        changes.forEach { change ->
            if(!expensesMap.containsKey(change.item)) {
                expensesMap[change.item] = abs(change.amount)
            } else {
                expensesMap[change.item] = expensesMap[change.item]!! + abs(change.amount)
            }
        }
        return expensesMap.map { e -> Change(-1, e.key, e.value, LocalDate.now()) }
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