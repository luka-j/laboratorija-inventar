package rs.lukaj.laboratorija.inventar

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.*

@Controller
class ViewController(@Autowired val service: InventoryService) {
    companion object {
        val BEGGINING_OF_TIME: LocalDateTime = LocalDate.of(1970, Month.JANUARY, 1).atTime(0, 0, 0);

        val DASH_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm:ss")!!
        val SLASH_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm")!!
    }

    private val logger = KotlinLogging.logger {}

    @GetMapping("/")
    fun index(model: Model) : String {
        return "index"
    }

    @GetMapping("/state")
    fun mainTable(@RequestParam(required = false, defaultValue = "01-01-1970")
                  @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") date: LocalDateTime, model: Model) : String {
        val inventories = service.getAllInventories()
        val data = if(date == BEGGINING_OF_TIME) {
            model["date"] = LocalDateTime.now().format(SLASH_FORMATTER)
            service.getAllItems()
        } else {
            model["date"] = date.format(SLASH_FORMATTER)
            service.getAllItems(date)
        }
        model["data"] = data
        model["showDobavljac"] = data.any { i -> i.dobavljac.isNotBlank() }
        model["inventories"] = inventories
        return "mainTable"
    }

    @GetMapping("/edit")
    fun editTable(model: Model) : String {
        val inventories = service.getAllInventories()
        val data = service.getAllItems()
        model["showDobavljac"] = data.any { i -> i.dobavljac.isNotBlank() }
        model["data"] = data
        model["inventories"] = inventories
        return "editItemsTable"
    }

    @GetMapping("/ugovori")
    fun ugovori(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Ugovori"
        model["removeFrom"] = -1
        model["addTo"] = 3
        model["reversal"] = false
        model["hasExpirationTime"] = true
        return "addChanges"
    }
    @GetMapping("/ugovori/storno")
    fun stornoUgovor(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "STORNO Ugovori"
        model["removeFrom"] = 3
        model["addTo"] = -1
        model["reversal"] = true
        model["hasExpirationTime"] = false
        return "addChanges"
    }
    @GetMapping("/trebovanja")
    fun trebovanja(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Trebovanja"
        model["removeFrom"] = 3
        model["addTo"] = 1
        model["reversal"] = false
        model["hasExpirationTime"] = false
        return "addChanges"
    }
    @GetMapping("/trebovanja/storno")
    fun stornoTrebovanje(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "STORNO Trebovanja"
        model["removeFrom"] = 1
        model["addTo"] = 3
        model["reversal"] = true
        model["hasExpirationTime"] = false
        return "addChanges"
    }
    @GetMapping("/ulaz")
    fun ulaz(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Računi"
        model["removeFrom"] = 1
        model["addTo"] = 2
        model["reversal"] = false
        model["hasExpirationTime"] = false
        return "addChanges"
    }
    @GetMapping("/izlaz")
    fun izlaz(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Utrošak"
        model["removeFrom"] = 2
        model["addTo"] = -1
        model["reversal"] = false
        model["hasExpirationTime"] = false
        return "addChanges"
    }

    @GetMapping("/dodaj")
    fun addItems(model : Model) : String {
        return "addItem"
    }

    @GetMapping("/changes")
    fun allChangesSince(@RequestParam(required = false, defaultValue = "01-01-1970T00:00:00")
                        @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") date: LocalDateTime,
                        @RequestParam(required = false, defaultValue = "01-01-2038T23:59:59")
                        @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") until: LocalDateTime,
                        @RequestParam(required = false, defaultValue = "") inventory: String,
                         model: Model) : String {
        val data = service.getAllChangesSince(date, until, inventory)
        val dateStr = date.format(DASH_FORMATTER)
        val untilStr = until.format(DASH_FORMATTER)
        model["data"] = data
        model["aggregated"] = false
        model["priceAggregate"] = false
        model["redirectUrl"] = "/changes?date=$dateStr&inventory=$inventory&until=$untilStr"
        model["title"] = "Promene"
        return "changes"
    }

    @GetMapping("/expenses")
    fun allExpensesSince(@RequestParam(required = false, defaultValue = "01-01-1970T00:00:00")
                         @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") date: LocalDateTime,
                         @RequestParam(required = false, defaultValue = "01-01-2038T23:59:59")
                         @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") until: LocalDateTime,
                        @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllExpensesSince(date, until, inventory, false)
        initAggregateModel(data, inventory, date, until, model)
        model["title"] = "Utrošeno"
        model["type"] = "expenses"
        return "changes"
    }

    @GetMapping("/purchases")
    fun allPurchasesSince(@RequestParam(required = false, defaultValue = "01-01-1970T00:00:00")
                          @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") date: LocalDateTime,
                          @RequestParam(required = false, defaultValue = "01-01-2038T23:59:59")
                          @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") until: LocalDateTime,
                          @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllPurchasesSince(date, until, inventory, false)
        initAggregateModel(data, inventory, date, until, model)
        model["title"] = "Nabavke"
        model["type"] = "purchases"
        return "changes"
    }

    @GetMapping("/prices")
    fun allPricesSince(@RequestParam(required = false, defaultValue = "01-01-1970T00:00:00")
                       @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") date: LocalDateTime,
                       @RequestParam(required = false, defaultValue = "01-01-2038T23:59:59")
                       @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") until: LocalDateTime,
                       @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllPricesSince(date, until, inventory)
        initAggregateModel(data, inventory, date, until, model)
        model["title"] = "Cene"
        model["priceAggregate"] = true
        return "changes"
    }

    @GetMapping("/report")
    fun report(@RequestParam(required = false, defaultValue = "01-01-1970T00:00:00")
               @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") date: LocalDateTime,
               @RequestParam(required = false, defaultValue = "01-01-2038T23:59:59")
               @DateTimeFormat(pattern="dd-MM-yyyy'T'HH:mm:ss") until: LocalDateTime,
               @RequestParam columns: List<String>, @RequestParam colnames: List<String>,
               model: Model) : String {
        logger.info { "Generating report for expressions $columns, colnames $colnames" }
        val changesByInventory = columns.map { col ->
            if(col.startsWith('-')) service.getAllExpensesSince(date, until, col.substring(1), false)
            else if(col.startsWith('+') || col.startsWith(' ')) service.getAllPurchasesSince(date, until, col.substring(1), false)
            else if(col.startsWith('~')) service.getAllExpensesSince(date, until, col.substring(1), true)
            else throw java.lang.IllegalArgumentException("Unknown colname ${col}!")
        }
        val changesByItem = HashMap<Item, Array<Change?>>()
        for(i in changesByInventory.indices) {
            changesByInventory[i].forEach {
                if(changesByItem[it.item.item] == null) changesByItem[it.item.item] = arrayOfNulls(changesByInventory.size)
                changesByItem[it.item.item]!![i] = it
            }
        }
        val sortedMap : SortedMap<Item, List<Double>> = TreeMap (Comparator.comparing<Item, Int> { it.brPartije }
                .then(Comparator.comparing<Item, Int> { it.brStavke }))
        sortedMap.putAll(changesByItem.mapValues { changes -> changes.value.map { it?.amount ?: 0.0 } })
        model["colnames"] = colnames
        model["data"] = sortedMap
        model["date"] = date.format(SLASH_FORMATTER)
        model["untilDate"] = until.format(SLASH_FORMATTER)
        return "report"
    }

    @GetMapping("/item")
    fun itemHistory(@RequestParam brPartije: Int, @RequestParam brStavke: Int, model: Model) : String {
        val item = service.getItem(brPartije, brStavke)
        model["item"] = item
        model["history"] = service.getItemHistory(item)
        return "itemHistory"
    }

    private fun initAggregateModel(data: List<Change>, inventory: String, date: LocalDateTime, until: LocalDateTime, model: Model) {
        val dateStr = date.format(DASH_FORMATTER)
        val untilStr = until.format(DASH_FORMATTER)
        model["data"] = data
        model["aggregated"] = true
        model["inventory"] = inventory
        model["date"] = dateStr
        model["untilDate"] = untilStr
        model["priceAggregate"] = false
    }

    @GetMapping("/reset")
    fun resetInventory(@RequestParam name: String) : ResponseEntity<Any> {
        service.resetInventory(name)
        return ResponseEntity.ok("Reset $name successfully!")
    }

    //todo pageable pregled izmena?
}
