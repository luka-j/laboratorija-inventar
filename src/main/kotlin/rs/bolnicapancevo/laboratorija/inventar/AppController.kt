package rs.bolnicapancevo.laboratorija.inventar

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.websocket.server.PathParam

@Controller
class AppController(@Autowired val service: CrudService) {
    companion object {
        val LOGGER = LoggerFactory.getLogger(AppController::class.java)
    }

    @PostMapping("/api/setAmount")
    fun changeAmount(@RequestParam amount: Double,
                     @RequestParam brPartije: Int,
                     @RequestParam brStavke: Int,
                     @RequestParam inventoryName: String,
                     model: Model) : String {
        if(amount < 0) {
            LOGGER.warn("Setting negative amount for ({}, {}) in {}!", brPartije, brStavke, amount)
        }
        val item = service.getItem(brPartije, brStavke)
        val inventory = service.getInventory(inventoryName)
        service.setItemAmount(item, inventory, amount)

        model["status"] = "Done"
        return "status"
    }

    @PostMapping("/api/deleteChange")
    fun deleteChange(@RequestParam id: Int, @RequestParam redirectUrl: String) : ResponseEntity<Any> {
        service.revertChange(id)
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", redirectUrl).build()
    }

    @PostMapping("/api/{inventory}/{date}/{type}/report")
    fun generateReport(@RequestParam table: MultipartFile, @PathVariable inventory: String,
                       @PathVariable @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                       @PathVariable type: String) : ResponseEntity<Any>{
        val changes = when {
            type.equals("expenses", true) -> service.getAllExpensesSince(date, inventory)
            type.equals("purchases", true) -> service.getAllPurchasesSince(date, inventory)
            else -> service.getAllChangesSince(date, inventory)
        }
        //todo match changes to table
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("WIP. Uploaded: ${table.size}")
    }

    @GetMapping("/")
    fun mainTable(model: Model) : String {
        val inventories = service.getAllInventories()
        val data = service.getAllItems()
        model["data"] = data
        model["inventories"] = inventories
        return "mainTable"
    }

    @GetMapping("/changes")
    fun allChangesSince(@RequestParam(required = false, defaultValue = "01-01-1970")
                        @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                        @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllChangesSince(date, inventory)
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        model["data"] = data
        model["aggregated"] = false
        model["redirectUrl"] = "/changes?date=" + dateStr + "&inventory=" + inventory
        model["inventory"] = inventory
        model["date"] = dateStr
        model["type"] = "all"
        return "changes"
    }

    @GetMapping("/expenses")
    fun allExpensesSince(@RequestParam(required = false, defaultValue = "01-01-1970")
                         @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                        @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllExpensesSince(date, inventory)
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        model["data"] = data
        model["aggregated"] = true
        model["redirectUrl"] = "/changes?date=" + dateStr + "&inventory=" + inventory
        model["inventory"] = inventory
        model["date"] = dateStr
        model["type"] = "expenses"
        return "changes"
    }

    @GetMapping("/purchases")
    fun allPurchasesSince(@RequestParam(required = false, defaultValue = "01-01-1970")
                         @DateTimeFormat(pattern="dd-MM-yyyy") date: LocalDate,
                         @RequestParam(required = false, defaultValue = "") inventory: String, model: Model) : String {
        val data = service.getAllPurchasesSince(date, inventory)
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        model["data"] = data
        model["aggregated"] = true
        model["redirectUrl"] = "/purchases?date=" + dateStr + "&inventory=" + inventory
        model["inventory"] = inventory
        model["date"] = dateStr
        model["type"] = "purchases"
        return "changes"
    }

    @GetMapping("/reset")
    fun resetInventory(@RequestParam name: String) : ResponseEntity<Any> {
        service.resetInventory(name)
        return ResponseEntity.ok("Reset $name successfully!")
    }
}