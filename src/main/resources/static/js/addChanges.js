let inputs = document.getElementsByName("amount")
let btn = document.getElementById('submit-btn')
let datePicker = document.getElementsByName('datum')[0]
let amounts = new Map()
let errors = new Set()

window.onbeforeunload = function () {
    if(amounts.size > 0) {
        return "Podaci nisu sačuvani. Napusti stranicu bez čuvanja?"
    } else {
        return false
    }
}

for(let input of inputs) {
    input.addEventListener('change', function () {
        let brPartije = input.dataset.brPartije
        let brStavke = input.dataset.brStavke
        let value = input.value
        console.log("brPartije: " + brPartije + ", brStavke: " + brStavke + ", value: " + value + "\n")
        let check = document.getElementById("check-" + brPartije + "-" + brStavke)
        let cross = document.getElementById("cross-" + brPartije + "-" + brStavke)
        check.hidden = true
        cross.hidden = true
        if(value === "" || value === "0") {
            amounts.delete(brPartije + '-' + brStavke)
            errors.delete(brPartije + '-' + brStavke)
            if(errors.size === 0) enableButton()
            return
        }
        amounts.set(brPartije + '-' + brStavke, value)

        if(value < 0) {
            errors.add(brPartije + '-' + brStavke)
            cross.hidden = false
            disableButton()

        } else if(invRemoveFrom >= 0) {
            fetch('/api/available?invId=' + invRemoveFrom + '&brPartije=' + brPartije + '&brStavke=' + brStavke +
                '&amount=' + value + '&date=' + datePicker.value)
                .then(data => {
                    if(data.status === 200) {
                        errors.delete(brPartije + '-' + brStavke)
                        if(errors.size === 0) enableButton()
                        check.hidden = false
                    } else if(data.status === 400) {
                        errors.add(brPartije + '-' + brStavke)
                        disableButton()
                        cross.hidden = false
                    } else {
                        console.log('Error response: ' + data)
                        cross.hidden = false
                        check.hidden = false
                        disableButton()
                    }
                })
                .catch(err => console.log(err))
        } else {
            check.hidden = false
            errors.delete(brPartije + '-' + brStavke)
            if(errors.size === 0) enableButton()
        }
    })
}

datePicker.addEventListener('change', function () {
    if ("createEvent" in document) {
        let evt = document.createEvent("HTMLEvents");
        evt.initEvent("change", false, true);
        for(let input of inputs) if(input.value !== '') input.dispatchEvent(evt);
    }
    else
        for(let input of inputs) if(input.value !== '') input.fireEvent("onchange");
})

btn.addEventListener('click', function() {
    disableButton()
    let body = {
        from: invRemoveFrom,
        to: invAddTo,
        time: datePicker.value,
        items: []
    }
    for(const [key, value] of amounts) {
        let item = {
            brPartije: key.split('-')[0],
            brStavke: key.split('-')[1],
            amount: value
        }
        body.items.push(item)
    }
    fetch('/api/transfer', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Transfer-Type': isReversal ? 'Reversal' : 'Standard'
        },
        body: JSON.stringify(body)
    }).then(data => {
        if(data.status === 200) {
            window.onbeforeunload = undefined
            window.location.href = '/'
        } else {
            window.alert('Doslo je do greske')
            console.log(data)
            enableButton()
        }
    }).catch(err => {
        window.alert('Doslo je do greske u komunikaciji')
        console.error(err)
        enableButton()
    })
})