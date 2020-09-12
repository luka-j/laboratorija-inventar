package rs.bolnicapancevo.laboratorija.inventar

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
import java.time.format.DateTimeFormatter

@Controller
class ViewController(@Autowired val service: CrudService) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(ViewController::class.java)
    }

    @GetMapping("/state")
    fun mainTable(model: Model) : String {
        val inventories = service.getAllInventories()
        val data = service.getAllItems()
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
        model["title"] = "Ulaz"
        model["removeFrom"] = 1
        model["addTo"] = 2
        return "addChanges"
    }
    @GetMapping("/izlaz")
    fun izlaz(model: Model) : String {
        val data = service.getAllItems()
        model["data"] = data
        model["title"] = "Izlaz"
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
        val time = date.atTime(0, 0, 0)
        val timeUntil = until.atTime(0, 0, 0)
        val data = service.getAllChangesSince(time, timeUntil, inventory)
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val untilStr = until.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
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
        val time = date.atTime(0, 0, 0)
        val timeUntil = until.atTime(0, 0, 0)
        val data = service.getAllExpensesSince(time, timeUntil, inventory)
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
        val time = date.atTime(0, 0, 0)
        val timeUntil = until.atTime(0, 0, 0)
        val data = service.getAllPurchasesSince(time, timeUntil, inventory)
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
        val time = date.atTime(0, 0, 0)
        val timeUntil = until.atTime(0, 0, 0)
        val data = service.getAllPricesSince(time, timeUntil, inventory)
        initAggregateModel(data, inventory, date, until, model)
        model["title"] = "Cene"
        model["priceAggregate"] = true
        return "changes"
    }

    private fun initAggregateModel(data: List<Change>, inventory: String, date: LocalDate, until: LocalDate, model: Model) {
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val untilStr = until.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
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
}