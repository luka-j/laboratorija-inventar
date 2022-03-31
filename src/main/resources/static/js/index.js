Date.prototype.toDateInputValue = (function() {
    let local = new Date(this);
    local.setMinutes(this.getMinutes() - this.getTimezoneOffset());
    return local.toJSON().slice(0,10);
})
Date.prototype.toDashString = (function () {
    let dateInMonth = this.getDate() < 10 ? '0' + this.getDate() : this.getDate()
    let month = this.getMonth() < 9 ? '0' + (this.getMonth() + 1).toString() : (this.getMonth()+1).toString()
    return dateInMonth + '-' + month + '-' + this.getFullYear()
})
const today = new Date()

let datePicker = document.getElementsByName('state-datum')[0]
datePicker.value = today.toDateInputValue()

document.getElementById('state').addEventListener('click', function () {
    let date = new Date(datePicker.value)
    window.location.href = `/state?date=${date.toDashString()}`
})

let reportDate = document.getElementsByName('report-datum')[0]
const oneMonthAgo = new Date()
oneMonthAgo.setMonth(today.getMonth()-1)
reportDate.value = oneMonthAgo.toDateInputValue()
let reportUntil = document.getElementsByName('report-until')[0]
reportUntil.value = today.toDateInputValue()
document.getElementById('report').addEventListener('click', function () {
    let date = new Date(reportDate.value)
    let until = new Date(reportUntil.value)
    window.location.href = `/report?date=${date.toDashString()}&until=${until.toDashString()}&columns=+Saglasnosti&columns=+Preostalo na ugovoru&columns=+Stanje&columns=-Stanje&colnames=Saglasnosti&colnames=Ugovori&colnames=Računi&colnames=Utrošak`
})