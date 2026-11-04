function checkIfInsideFigure(r, x, y) {
    if (x <= 0 && x >= -r && y <= 0 && y >= -r / 2) {
        return true;
    }

    if (y <= 0 && x >= 0 && y >= x / 2 - r / 2) {
        return true;
    }

    if (x >= 0 && y >= 0 && x * x + y * y <= r * r) {
        return true;
    }

    return false;
}

const canvas = document.getElementById("graph");
const context = canvas.getContext("2d");

context.lineWidth = 1;
context.fillStyle = "#378CFA";

drawFigure(context, [3]);

context.strokeStyle = "black";
drawCoords(canvas, context);

loadChecks();

const form = document.getElementById("check-form");
const x_error = document.querySelector("#x-error");
const y_error = document.querySelector("#y-error");
const r_error = document.querySelector("#r-error");
const clear_button = document.querySelector(".clear-group button");
const r_element = document.querySelector("#r-checkbox");
const y_element = document.querySelector("#y-input");
const x_element = document.querySelector("#x-select");
const table_button = document.querySelector(".tablebutton-group button");
const bad_error_label = document.querySelector("#bad_error");

let current_x;
let current_y;
let current_color;
let wasDrawn = false;
let pointActive = false;

const INT = /^-?\d{1,9}$/;
const DECIMAL = /^-?\d{1,20}([.,]\d{1,40})?$/;
const VALID_MESSAGE = "Р’РµСЂРЅРѕРµ Р·РЅР°С‡РµРЅРёРµ.";

class ValidationException extends Error {}

function validateX(s) {
    if (s == null || s.trim() === "") {
        throw new ValidationException("");
    }
    const x = Number(s);
    return x;
}
 
function validateY(s) {
    if (s == null || s.trim() === "") {
        throw new ValidationException("");
    }
    if (!DECIMAL.test(s.trim())) {
        throw new ValidationException("");
    }
    const y = Number(s.trim().replace(",", "."));
    if (y <= -5 || y >= 5) {
        throw new ValidationException("");
    }
    return y;
}

function clearErrors() {
    x_error.textContent = "";
    y_error.textContent = "";
    r_error.textContent = "";
}
 
function showValidationErrors(data) {
    const order = ["time", "execTime", "x_result", "y_result", "r_result"];
    order.forEach(key => {
        console.log(key, data[key]);
    });
    x_error.textContent = data.x_result === VALID_MESSAGE ? "" : data.x_result;
    y_error.textContent = data.y_result === VALID_MESSAGE ? "" : data.y_result;
    r_error.textContent = data.r_result === VALID_MESSAGE ? "" : data.r_result;
}

function handleValid(data) {
    const order = ["x", "y", "result", "time", "execTime"];
    order.forEach(key => {
        console.log(key, data[key]);
    });
    const x = Number(data.x);
    const y = Number(data.y);
    const rs = data.result;
    context.clearRect(0, 0, canvas.width, canvas.height);
    drawFigure(context, rs.map(response => response.r));
    drawCoords(canvas, context);
    placePoint(context, x, y);
    rs.forEach(response => {
        addCheck(response.r, x, y, response.result, data.time, data.execTime);
    });
    current_x = x;
    current_y = y;
    wasDrawn = true;
    pointActive = true;
}

function handleServerAnswer(data) {
    if ("x_result" in data) {
        showValidationErrors(data);
    } else if ("result" in data) {
        clearErrors();
        handleValid(data);
    }
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    let is_wrong = false;
    let x;
    let y;
    let rs = [];
    bad_error_label.textContent = "";
 
    try {
        x = validateX(x_element.value);
        x_error.textContent = "";
    } catch (e) {
        x_error.textContent = e.message;
        x_element.classList.add("error");
        is_wrong = true;
    }
 
    try {
        y = validateY(y_element.value);
        y_error.textContent = "";
        y_element.classList.remove("error");
    } catch (e) {
        y_error.textContent = e.message;
        y_element.classList.add("error");
        is_wrong = true;
    }
 
    rs = Array.from(r_element.querySelectorAll('input[name="r"]:checked')).map(box => box.value);
    if (rs.length === 0) {
        r_error.textContent = "";
        is_wrong = true;
    } else {
        r_error.textContent = "";
    }
 
    const body = new URLSearchParams();
    body.append("x", x_element.value);
    body.append("y", y_element.value);
    rs.forEach(r => body.append("r", r));

    fetch("http://localhost:18080/fcgi-bin/server.jar", {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body
    }).then(response => {
        objectJson = response.json();
        return objectJson;
    }).then(data => {
        console.log(data);
        handleServerAnswer(data);
    }).catch(error => {
        if (error instanceof TypeError){
            console.error("Р’РѕР·РЅРёРєР»Рё РїСЂРѕР±Р»РµРјС‹ РІРѕ РІСЂРµРјСЏ СЃРѕРµРґРёРЅРµРЅРёСЏ СЃ СЃРµСЂРІРµСЂРѕРј.");
            bad_error_label.textContent = "Р’РѕР·РЅРёРєР»Рё РїСЂРѕР±Р»РµРјС‹ РІРѕ РІСЂРµРјСЏ СЃРѕРµРґРёРЅРµРЅРёСЏ СЃ СЃРµСЂРІРµСЂРѕРј.";
        }
        else if (error instanceof SyntaxError){
            console.error("Р’РѕР·РЅРёРєР»Рё РїСЂРѕР±Р»РµРјС‹ РїСЂРё РѕР±СЂР°Р±РѕС‚РєРµ СЃРµСЂРІРµСЂРѕРј Р·Р°РїСЂРѕСЃР°.");
            bad_error_label.textContent = "Р’РѕР·РЅРёРєР»Рё РїСЂРѕР±Р»РµРјС‹ РїСЂРё РѕР±СЂР°Р±РѕС‚РєРµ СЃРµСЂРІРµСЂРѕРј Р·Р°РїСЂРѕСЃР°.";
        }
        else {
            console.error("Р’РѕР·РЅРёРєР»Р° РЅРµРёР·РІРµСЃС‚РЅР°СЏ РѕС€РёР±РєР°.");
            console.error(error);
        }
    });
});


clear_button.addEventListener("click", () => {
    form.reset();
    x_element.classList.remove("error");
    y_element.classList.remove("error");
    x_error.textContent = "";
    y_error.textContent = "";
    r_error.textContent = "";
    bad_error_label.textContent = "";
});


table_button.addEventListener("click", () => {
    localStorage.removeItem("results");
    const tbody = document.getElementById("checks-body");
    while (tbody.firstChild){
        tbody.removeChild(tbody.firstChild);
    }
});

x_element.addEventListener("change", () => {
    x_element.classList.remove("error");
    x_error.textContent = "";
    bad_error_label.textContent = "";
});

y_element.addEventListener("input", () => {
    y_element.classList.remove("error");
    y_error.textContent = "";
    bad_error_label.textContent = "";
});


function loadChecks() {
    let results = JSON.parse(localStorage.getItem("results"));
    if (results === null) {
        results = [];
    }
    results.forEach(result => addRow(result));
}

function addCheck(r, x, y, result, time, execTime) {
    let results = JSON.parse(localStorage.getItem("results"));
    if (results === null) {
        results = [];
    }
    const check = {
        x: x,
        y: y,
        r: r,
        result: result,
        timestamp: formatDate(time),
        execTime: execTime
    };
    results.push(check);
    localStorage.setItem("results", JSON.stringify(results));
    addRow(check);
}


function addRow(record) {
    const tbody = document.getElementById("checks-body");
    const row = document.createElement("tr");
    row.innerHTML = `
        <td>${record.x}</td>
        <td>${record.y}</td>
        <td>${record.r}</td>
        <td class="${record.result ? "success-text" : "fail-text"}">
            ${record.result ? "РџРѕРїР°РґР°РЅРёРµ" : "РџСЂРѕРјР°С…"}
        </td>
        <td>${record.timestamp}</td>
        <td>${record.execTime}</td>
    `;
    tbody.prepend(row);
}

function formatDate(timestamp) {
    return new Date(timestamp).toLocaleString("ru-RU");
}