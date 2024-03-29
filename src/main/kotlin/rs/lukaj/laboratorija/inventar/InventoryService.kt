package rs.lukaj.laboratorija.inventar

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import kotlin.math.abs

@Service
class InventoryService(@Autowired val inventoryRepository: InventoryRepository,
                       @Autowired val itemRepository: ItemRepository,
                       @Autowired val inventoryItemRepository: InventoryItemRepository,
                       @Autowired val changeRepository: ChangeRepository) {

    private val logger = KotlinLogging.logger {}

    companion object {
        val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    }

    fun addInventory(name: String): Inventory {
        val inv = Inventory(-1, name, ArrayList(0), 0, true)
        return inventoryRepository.save(inv)
    }

    fun addItems(items: List<ItemDTO>) : List<Int> {
        return items.stream().map { Item(-1, it.ime, it.dobavljac, it.brPartije, it.brStavke, 0.0, it.rgn, ArrayList()) }
                .map { itemRepository.save(it) }
                .map { it.id }
                .collect(Collectors.toList())
    }

    fun editItem(id: Int, field: String, newValue: String) {
        val maybeItem = itemRepository.findById(id)
        if(maybeItem.isEmpty) throw NotFoundException("Item $id doesn't exist!")
        val item = maybeItem.get()
        when (field) {
            "brPartije" -> item.brPartije = newValue.toInt()
            "brStavke" -> item.brStavke = newValue.toInt()
            "ime" -> item.ime = newValue
            "rgn" -> item.rgn = newValue
            "dobavljac" -> item.dobavljac = newValue
            else -> throw BadRequestException("Field $field doesn't exist")
        }

        val existingItem = itemRepository.findByBrPartijeAndBrStavke(item.brPartije, item.brStavke)
        if(existingItem.isPresent && existingItem.get().id != id)
            throw ConflictException("Item with brPartije ${item.brPartije} brStavke ${item.brStavke} already exists!")
        //^ when you don't do db constraints the right way on the first try
        itemRepository.save(item)
    }

    fun deleteItem(id: Int) {
        itemRepository.deleteById(id)
    }

    fun itemExists(brPartije: Int, brStavke: Int) : Boolean {
        return itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke).isPresent
    }

    fun addItemToRepository(invName: String, id1: Int, id2: Int, kolicina: Double) : InventoryItem {
        return inventoryRepository.findByIme(invName).flatMap { inv ->
            itemRepository.findByBrPartijeAndBrStavke(id1, id2).map { item ->
                val inventoryItem = InventoryItem(-1, inv, item, kolicina, ArrayList())
                inventoryItemRepository.save(inventoryItem)
            }
        }.orElseThrow { NotFoundException("Not found!") }
    }

    fun isItemAvailable(invId: Int, brPartije: Int, brStavke: Int, kolicina: Double, date: LocalDateTime) : Boolean {
        return inventoryRepository.findById(invId).flatMap { inv ->
            itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke).map { item ->
                 inventoryItemRepository.findByInventoryAndItem(inv, item)
                        .map {
                            if(!date.isBefore(LocalDateTime.now())) it.kolicina
                            else {
                                it.kolicina - getAllChangesForItem(date, LocalDateTime.now(), it).map { it.amount }.sum()
                            }
                        }
                        .map { amount -> amount >= kolicina }
                        .orElse(false)
            }
        }.orElseThrow { NotFoundException("Not found!") }
    }

    fun setItemAmount(item: Item, inventory: Inventory, amount: Double) : InventoryItem {
        if(!inventory.modifiable) throw BadRequestException("Cannot modify item in non-modifiable inventory!")
        val invItem = inventoryItemRepository.findByInventoryAndItem(inventory, item)
        var prevAmount = 0.0
        val changedInvItem: InventoryItem
        if(invItem.isEmpty) {
            logger.info { "Adding $amount of item ${item.ime} to inventory ${inventory.ime}" }
            val newItem = InventoryItem(-1, inventory, item, amount, ArrayList());
            changedInvItem = inventoryItemRepository.save(newItem)
        } else {
            val ii = invItem.get()
            logger.info { "Changing amount of ${item.ime} from ${ii.kolicina} to $amount in inventory ${inventory.ime}" }
            prevAmount = ii.kolicina
            ii.kolicina = amount
            changedInvItem = inventoryItemRepository.save(ii)
        }

        if(amount != prevAmount) {
            val change = Change(-1, changedInvItem, amount - prevAmount, LocalDateTime.now(), null)
            changeRepository.save(change)
        }

        return changedInvItem
    }

    fun revertChange(id: Long) {
        val change = changeRepository.findById(id).orElseThrow { NotFoundException("Change not found!") }
        logger.info { "Reverting change of ${change.amount} for item ${change.item.item.id} in inventory ${change.item.inventory.ime}" }
        change.item.kolicina -= change.amount
        inventoryItemRepository.save(change.item)
        changeRepository.deleteById(id)
    }

    @Transactional
    fun transfer(data: TransferRequest, isReversal : Boolean) {
        val requestDate = LocalDateTime.from(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").parse(data.time))
        if(data.from != -1) {
            val from = inventoryRepository.findById(data.from).orElseThrow { NotFoundException("Not found from inventory!") }
            if(!from.modifiable) throw BadRequestException("Cannot transfer from nonmodifiable inventory!")
            for(itemData in data.items) {
                val item = itemRepository.findByBrPartijeAndBrStavke(itemData.brPartije, itemData.brStavke).orElseThrow {IllegalStateException()}
                val fromItem = inventoryItemRepository.findByInventoryAndItem(from, item).orElseThrow {IllegalStateException()}
                fromItem.kolicina -= itemData.amount
                val savedItem = inventoryItemRepository.save(fromItem)
                var expirationTime: LocalDateTime? = null
                if(data.expirationTime != null) {
                    expirationTime = LocalDateTime.from(DATETIME_FORMATTER.parse(data.expirationTime))
                }
                val changeFrom = Change(-1, savedItem, -itemData.amount, requestDate,expirationTime,
                        if(isReversal) ChangeType.REVERSAL else ChangeType.ORDINARY)
                changeRepository.save(changeFrom)
            }
        }
        if(data.to != -1) {
            val to = inventoryRepository.findById(data.to).orElseThrow { NotFoundException("Not found to inventory!") }
            if(!to.modifiable) throw BadRequestException("Cannot transfer from nonmodifiable inventory!")
            for(itemData in data.items) {
                val item = itemRepository.findByBrPartijeAndBrStavke(itemData.brPartije, itemData.brStavke).orElseThrow {IllegalStateException()}
                val toItem = inventoryItemRepository.findByInventoryAndItem(to, item).map { toItem ->
                    toItem.kolicina += itemData.amount
                    toItem
                }.orElse(InventoryItem(-1, to, item, itemData.amount, ArrayList()))
                val savedItem = inventoryItemRepository.save(toItem)
                var expirationTime: LocalDateTime? = null
                if(data.expirationTime != null) {
                    expirationTime = LocalDateTime.from(DATETIME_FORMATTER.parse(data.expirationTime))
                }
                val changeTo = Change(-1, savedItem, itemData.amount, requestDate, expirationTime,
                        if(isReversal) ChangeType.REVERSAL else ChangeType.ORDINARY)
                changeRepository.save(changeTo)
            }
        }
    }

    fun getInventory(name: String) : Inventory {
        val maybeInventory = inventoryRepository.findByIme(name)
        return maybeInventory.orElseThrow { NotFoundException("Not found!") }
    }

    fun getItem(brPartije: Int, brStavke: Int) : Item {
        return itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke).orElseThrow { NotFoundException("Not found!") }
    }

    fun getItemIfExists(brPartije: Int, brStavke: Int) = itemRepository.findByBrPartijeAndBrStavke(brPartije, brStavke)

    fun getAllItems() : List<ItemWithMappedInventory> =
            itemRepository.findAll().toMutableList().map { item -> ItemWithMappedInventory(item) }
                .sortedWith(compareBy(ItemWithMappedInventory::brPartije, ItemWithMappedInventory::brStavke))


    fun getAllInventories() : List<Inventory> = inventoryRepository.findAll().toMutableList().sortedBy { i -> i.sortOrder }

    fun getAllItems(date: LocalDateTime) : List<ItemWithMappedInventory> {
        val itemMap = HashMap<Int, ItemWithMappedInventory>()
        itemRepository.findAll().map { item -> ItemWithMappedInventory(item) }.forEach { i -> itemMap[i.id] = i }
        getAllChangesSince(date, LocalDateTime.now(), "").forEach { change ->
            val inv = change.item.inventory.ime
            val item = itemMap[change.item.item.id]!!
            item.amounts[inv] = item.amounts.getOrDefault(inv, 0.0) - change.amount
        }
        return itemMap.values.toList().sortedWith(compareBy(ItemWithMappedInventory::brPartije, ItemWithMappedInventory::brStavke))
    }

    fun getAllChangesSince(date: LocalDateTime, until: LocalDateTime, inventory: String) : List<Change> {
        val allChanges = changeRepository.findAllByDateGreaterThanEqualAndDateLessThanEqual(date, until)
        return if(inventory.isEmpty()) allChanges.sortedByDescending { change -> change.date }
        else {
            val inventories = inventory.split(",").map { inv -> inv.trim().lowercase() }.toHashSet()
            return allChanges.filter { change -> inventories.contains(change.item.inventory.ime.lowercase()) }
                    .sortedByDescending { change -> change.date }
        }
    }

    fun getAllChangesForItem(since: LocalDateTime, until: LocalDateTime, inventoryItem: InventoryItem) =
         changeRepository.findAllByDateGreaterThanEqualAndDateLessThanEqualAndItemEquals(since, until, inventoryItem)


    fun getAllPricesSince(date: LocalDateTime, until: LocalDateTime, inventory: String) : List<Change> {
        val purchases = getAllPurchasesAsMap(date, until, inventory, false)
        val aggregated = HashMap<Int, MutablePair<InventoryItem, Double>>()
        purchases.forEach { (item, amount) ->
            val p = item.item.brPartije
            if(aggregated.containsKey(p)) {
                aggregated[p]!!.second += amount*item.item.cena
            } else {
                aggregated[p] = MutablePair(item, amount*item.item.cena)
            }
        }
        return aggregated.map { e -> Change(-1, e.value.first, e.value.second, LocalDateTime.now(), null) }
    }

    fun getAllExpensesAsMap(date: LocalDateTime, until: LocalDateTime, inventory: String, reversals: Boolean) : Map<InventoryItem, Double> {
        return aggregateChanges(changeRepository
                .findAllByAmountLessThanAndDateGreaterThanEqualAndDateLessThanEqualAndTypeEquals(
                    0.0, date, until, if(reversals) ChangeType.REVERSAL else ChangeType.ORDINARY), inventory)
    }

    fun getAllExpensesSince(date: LocalDateTime, until: LocalDateTime, inventory: String, reversals: Boolean) : List<Change> {
        return changesToList(getAllExpensesAsMap(date, until, inventory, reversals))
    }

    fun getAllPurchasesAsMap(date: LocalDateTime, until: LocalDateTime, inventory: String, reversals: Boolean) : Map<InventoryItem, Double> {
        return aggregateChanges(changeRepository
                .findAllByAmountGreaterThanAndDateGreaterThanEqualAndDateLessThanEqualAndTypeEquals(
                    0.0, date, until, if(reversals) ChangeType.REVERSAL else ChangeType.ORDINARY), inventory)
    }

    fun getAllPurchasesSince(date: LocalDateTime, until: LocalDateTime, inventory: String, reversals: Boolean) : List<Change> {
        return changesToList(getAllPurchasesAsMap(date, until, inventory, reversals))
    }

    fun resetInventory(name: String) {
        val inventory = inventoryRepository.findByIme(name).orElseThrow { NotFoundException("Inventory doesn't exist!") }

        logger.info { "Resetting inventory $name" }
        for(item in inventory.items) {
            item.kolicina = 0.0
            inventoryItemRepository.save(item)
        }
    }

    fun getItemHistory(item: Item) : List<ItemHistoryDTO> {
        val changes = item.inventory.flatMap { ii -> ii.changes }.sortedBy { c -> c.id }

        val history = ArrayList<ItemHistoryDTO>()
        val li = changes.listIterator()
        while(li.hasNext()) {
            val change = li.next()
            if(li.hasNext()) {
                val nextIndex = li.nextIndex()
                val nextChange = changes[nextIndex]
                if(change.amount < 0 && change.amount + nextChange.amount == 0.0 && nextChange.date == change.date) {
                    val amount = abs(change.amount)
                    val source = if(change.amount >= 0) nextChange.item.inventory.ime else change.item.inventory.ime
                    val destination = if(change.amount < 0) nextChange.item.inventory.ime else change.item.inventory.ime
                    if(change.type != nextChange.type) {
                        logger.warn { "Type not matching for subsequent changes! Change 1: $change | Change 2: $nextChange" }
                    }
                    history.add(ItemHistoryDTO(source, destination, amount, change.date, change.type == ChangeType.REVERSAL))
                    li.next()
                    continue
                }
            }

            if(change.amount < 0) history.add(ItemHistoryDTO(change.item.inventory.ime, "", change.amount,
                    change.date, change.type == ChangeType.REVERSAL))
            else history.add(ItemHistoryDTO("", change.item.inventory.ime, change.amount, change.date,
                    change.type == ChangeType.REVERSAL))
        }

        return history
    }

    private fun aggregateChanges(list : List<Change>, inventory: String) : Map<InventoryItem, Double> {
        var changes = list
        val inventories = inventory.split(",").map { inv -> inv.trim().lowercase() }.toHashSet()
        if(inventory.isNotEmpty()) {
            changes = changes.filter { change -> inventories.contains(change.item.inventory.ime.lowercase()) }
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
        return map.map { e -> Change(-1, e.key, e.value, LocalDateTime.now(), null) }.sortedByDescending { c -> c.date }
    }
}
