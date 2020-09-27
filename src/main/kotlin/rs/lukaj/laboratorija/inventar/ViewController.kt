package rs.lukaj.laboratorija.inventar

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Controller
class ViewController(@Autowired val service: CrudService) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(ViewController::class.java)
        val BEGGINING_OF_TIME: LocalDate = LocalDate.of(1970, Month.JANUARY, 1)

        val DASH_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy")!!
        val SLASH_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")!!
    }

    @GetMapping("/")
    fun index(model: Model) : String {
        return "index"
    }

    @GetMapping("/state")
    fun mainTable(@RequestParam(required = false, defaultValue = "01-01-1970")
                  @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate, model: Model) : String {
        val inventories = service.getAllInventories()
        val data = if(date == BEGGINING_OF_TIME) {
            model["date"] = LocalDate.now().format(SLASH_FORMATTER)
            service.getAllItems()
        } else {
            model["date"] = date.format(SLASH_FORMATTER)
            service.getAllItems(date)
        }
        model["data"] = data
        model["inventories"] = inventories
        return "mainTable"
    }

    @GetMapping("/ugovori")
    fun ugovori(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Ugovori"
        model["removeFrom"] = -1
        model["addTo"] = 1
        return "addChanges"
    }
    @GetMapping("/ulaz")
    fun ulaz(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Računi"
        model["removeFrom"] = 1
        model["addTo"] = 2
        return "addChanges"
    }
    @GetMapping("/izlaz")
    fun izlaz(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Utrošak"
        model["removeFrom"] = 2
        model["addTo"] = -1
        return "addChanges"
    }

    @GetMapping("/changes")
    fun allChangesSince(@RequestParam(required = false, defaultValue = "01-01-1970")
                        @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                        @RequestParam(required = false, defaultValue = "01-01-2038")
                        @DateTimeFormat(pattern="dd-MM-yyyy") until: LocalDate,
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
    fun allExpensesSince(@RequestParam(required = false, defaultValue = "01-01-1970")
                         @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                         @RequestParam(required = false, defaultValue = "01-01-2038")
                         @DateTimeFormat(pattern="dd-MM-yyyy") until: LocalDate,
                        @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllExpensesSince(date, until, inventory)
        initAggregateModel(data, inventory, date, until, model)
        model["title"] = "Utrošeno"
        model["type"] = "expenses"
        return "changes"
    }

    @GetMapping("/purchases")
    fun allPurchasesSince(@RequestParam(required = false, defaultValue = "01-01-1970")
                          @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                          @RequestParam(required = false, defaultValue = "01-01-2038")
                          @DateTimeFormat(pattern="dd-MM-yyyy") until: LocalDate,
                          @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllPurchasesSince(date, until, inventory)
        initAggregateModel(data, inventory, date, until, model)
        model["title"] = "Nabavke"
        model["type"] = "purchases"
        return "changes"
    }

    @GetMapping("/prices")
    fun allPricesSince(@RequestParam(required = false, defaultValue = "01-01-1970")
                       @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                       @RequestParam(required = false, defaultValue = "01-01-2038")
                       @DateTimeFormat(pattern="dd-MM-yyyy") until: LocalDate,
                       @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllPricesSince(date, until, inventory)
        initAggregateModel(data, inventory, date, until, model)
        model["title"] = "Cene"
        model["priceAggregate"] = true
        return "changes"
    }

    @GetMapping("/report")
    fun report(@RequestParam(required = false, defaultValue = "01-01-1970")
               @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
               @RequestParam(required = false, defaultValue = "01-01-2038")
               @DateTimeFormat(pattern="dd-MM-yyyy") until: LocalDate,
               @RequestParam columns: List<String>, @RequestParam colnames: List<String>,
               model: Model) : String {
        LOGGER.info("Generating report for expressions {}, colnames {}", columns, colnames)
        val changesByInventory = columns.map { col ->
            if(col.startsWith('-')) service.getAllExpensesSince(date, until, col.substring(1))
            else service.getAllPurchasesSince(date, until, col.substring(1)) }
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

    private fun initAggregateModel(data: List<Change>, inventory: String, date: LocalDate, until: LocalDate, model: Model) {
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

    //todo pregled izmena od - do
    //todo pageable pregled izmena?
    //todo tricolumn izmene - dodato na ugovore, prihodovano na stanju, rashodovano
}
