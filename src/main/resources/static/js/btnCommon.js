function disableButton() {
    btn.disabled = true
    if(btn.classList.contains('btn-primary')) {
        btn.classList.remove('btn-primary')
        btn.classList.add('btn-outline-primary')
    }
}
function enableButton() {
    btn.disabled = false
    btn.classList.add('btn-primary')
    btn.classList.remove('btn-outline-primary')
}