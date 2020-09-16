package rs.lukaj.laboratorija.inventar

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api")
class ApiController(@Autowired val service: CrudService) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(ApiController::class.java)
        const val END_OF_TIME: String = "2400-11-16" //being optimistic here
    }

    @PostMapping("/setAmount")
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

    @PostMapping("/deleteChange")
    fun deleteChange(@RequestParam id: Long, @RequestParam redirectUrl: String) : ResponseEntity<Any> {
        service.revertChange(id)
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", redirectUrl).build()
    }

    @GetMapping("/available")
    fun isAvailable(@RequestParam invId: Int, @RequestParam brPartije: Int, @RequestParam brStavke: Int, @RequestParam amount: Double,
    @RequestParam(required = false, defaultValue = END_OF_TIME) @DateTimeFormat(pattern="yyyy-MM-dd") date: LocalDate) : ResponseEntity<Any> {
        return if(service.isItemAvailable(invId, brPartije, brStavke, amount, date))
            ResponseEntity.ok("")
        else
            ResponseEntity.badRequest().build()
    }

    @PostMapping("/transfer")
    fun transfer(@RequestBody requestBody: TransferRequest) : ResponseEntity<Any> {
        LOGGER.info("Doing transfer from repository ${requestBody.from} to ${requestBody.to} of ${requestBody.items.size} items")
        service.transfer(requestBody)
        return ResponseEntity.ok("")
    }

    @PostMapping("/{inventory}/{date}/{until}/{type}/report")
    fun generateReport(@RequestParam table: MultipartFile, @PathVariable inventory: String,
                       @PathVariable @DateTimeFormat(pattern="dd/MM/yyyy") date: LocalDate,
                       @PathVariable @DateTimeFormat(pattern="dd/MM/yyyy") until: LocalDate,
                       @PathVariable type: String) : ResponseEntity<Any> {
        LOGGER.info("Generating report...")
        val inventoryChanges = when {
            type.equals("expenses", true) -> service.getAllExpensesAsMap(date, until, inventory)
            type.equals("purchases", true) -> service.getAllPurchasesAsMap(date, until, inventory)
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
}