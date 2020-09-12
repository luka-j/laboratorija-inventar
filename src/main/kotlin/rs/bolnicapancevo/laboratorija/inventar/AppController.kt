package rs.bolnicapancevo.laboratorija.inventar

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
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
import java.io.File
import java.lang.IllegalArgumentException
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

        model["status"] = "Sačuvano"
        return "status"
    }

    @PostMapping("/api/deleteChange")
    fun deleteChange(@RequestParam id: Int, @RequestParam redirectUrl: String) : ResponseEntity<Any> {
        service.revertChange(id)
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", redirectUrl).build()
    }

    @GetMapping("/api/available")
    fun isAvailable(@RequestParam invId: Int, @RequestParam brPartije: Int, @RequestParam brStavke: Int, @RequestParam amount: Double) : ResponseEntity<Any> {
        return if(service.isItemAvailable(invId, brPartije, brStavke, amount))
            ResponseEntity.ok("")
        else
            ResponseEntity.badRequest().build()
    }

    @PostMapping("/api/transfer")
    fun transfer(@RequestBody requestBody: TransferRequest) : ResponseEntity<Any> {
        service.transfer(requestBody)
        return ResponseEntity.ok("")
    }

    @PostMapping("/api/{inventory}/{date}/{until}/{type}/report")
    fun generateReport(@RequestParam table: MultipartFile, @PathVariable inventory: String,
                       @PathVariable @DateTimeFormat(pattern="dd/MM/yyyy") date: LocalDate,
                       @PathVariable @DateTimeFormat(pattern="dd/MM/yyyy") until: LocalDate,
                       @PathVariable type: String) : ResponseEntity<Any>{
        LOGGER.info("Generating report...")
        val time = date.atTime(0, 0, 0)
        val timeUntil = until.atTime(0, 0, 0)
        val inventoryChanges = when {
            type.equals("expenses", true) -> service.getAllExpensesAsMap(time, timeUntil, inventory)
            type.equals("purchases", true) -> service.getAllPurchasesAsMap(time, timeUntil, inventory)
            else -> throw IllegalArgumentException("Invalid type!")
        }
        val changes = HashMap<Item, Double>()
        inventoryChanges.forEach {e ->
            if(changes.containsKey(e.key.item)) {
                changes[e.key.item] = changes[e.key.item]!! + e.value
            } else {
                changes[e.key.item] = e.value
            }
        }

        val output = StringBuilder()
        val excelFile = File(System.getProperty("java.io.tmpdir") + "/table.xlsx")
        excelFile.delete()
        table.transferTo(excelFile)
        val wb = XSSFWorkbook(excelFile)
        val sheet = wb.getSheetAt(0)
        var i = 0
        LOGGER.info("Starting to work on file")
        while(i < sheet.lastRowNum) {
            val row = sheet.getRow(i)
            val p = row.getCell(5)
            val s = row.getCell(7)
            if(p != null && s != null && p.cellType == CellType.NUMERIC) {
                break
            }
            i++
        }
        val empty = ArrayList<InventoryItem>(0)
        while(i < sheet.lastRowNum) {
            val row = sheet.getRow(i)
            if(row.getCell(5) == null || row.getCell(5).cellType != CellType.NUMERIC) break

            val p = row.getCell(5).numericCellValue.toInt()
            val s = row.getCell(7).numericCellValue.toInt()
            val item = Item(-1, "", "", p, s, -1.0, empty)
            if(changes.containsKey(item)) {
                output.append(changes[item]).append('\n')
            } else {
                output.append("0.0\n")
            }
            i++
        }

        return ResponseEntity.ok().header("Content-Type", "text/plain")
                .header("Content-Disposition", "inline").body(output)
    }

    @GetMapping("/allState")
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
    fun allChangesSince(@RequestParam(required = false, defaultValue = "01/01/1970")
                        @DateTimeFormat(pattern="dd/MM/yyyy") date: LocalDate,
                        @RequestParam(required = false, defaultValue = "01/01/2038")
                        @DateTimeFormat(pattern="dd/MM/yyyy") until: LocalDate,
                        @RequestParam(required = false, defaultValue = "") inventory: String,
                         model: Model) : String {
        val time = date.atTime(0, 0, 0)
        val timeUntil = until.atTime(0, 0, 0)
        val data = service.getAllChangesSince(time, timeUntil, inventory)
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val untilStr = until.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        model["data"] = data
        model["aggregated"] = false
        model["priceAggregate"] = false
        model["redirectUrl"] = "/changes?date=$dateStr&inventory=$inventory&until=$untilStr"
        model["title"] = "Promene"
        return "changes"
    }

    @GetMapping("/expenses")
    fun allExpensesSince(@RequestParam(required = false, defaultValue = "01/01/1970")
                         @DateTimeFormat(pattern="dd/MM/yyyy") date: LocalDate,
                         @RequestParam(required = false, defaultValue = "01/01/2038")
                         @DateTimeFormat(pattern="dd/MM/yyyy") until: LocalDate,
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
    fun allPurchasesSince(@RequestParam(required = false, defaultValue = "01/01/1970")
                          @DateTimeFormat(pattern="dd/MM/yyyy") date: LocalDate,
                          @RequestParam(required = false, defaultValue = "01/01/2038")
                          @DateTimeFormat(pattern="dd/MM/yyyy") until: LocalDate,
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
    fun allPricesSince(@RequestParam(required = false, defaultValue = "01/01/1970")
                       @DateTimeFormat(pattern="dd/MM/yyyy") date: LocalDate,
                       @RequestParam(required = false, defaultValue = "01/01/2038")
                       @DateTimeFormat(pattern="dd/MM/yyyy") until: LocalDate,
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
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val untilStr = until.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
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