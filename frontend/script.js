function sanitizeId(key) {
  // Replace colon (and potentially other problematic chars if needed)
  return key.replace(/:/g, "__");
}
// Configuration
const API_URL = "http://localhost:8000";
const WS_URL_BASE = `ws://${window.location.hostname}:8000/ws`;
const MAX_POINTS = 100; // Max points per dataset line

// --- NEW: Ping Interval Configuration ---
// Must be less than the server's KEEP_ALIVE_TIMEOUT_SECONDS (which is 60s)
const PING_INTERVAL_MS = 30000; // Send a ping every 30 seconds

// DOM Elements
const refreshButton = document.getElementById("refreshSessions");
const sessionListDiv = document.getElementById("sessionList");
const sessionListFeedback = document.getElementById("sessionListFeedback");
const messagesDiv = document.getElementById("messages");
const wsStatusSpan = document.getElementById("wsStatus");
const connectionCountSpan = document.getElementById("connectionCount"); // New analytics
const rawCountSpan = document.getElementById("rawCount");
const aggCountSpan = document.getElementById("aggCount");
const warnCountSpan = document.getElementById("warnCount");
const errorCountSpan = document.getElementById("errorCount");

// --- Connection Management ---
// Map Key: "sensorId:sensorSessionId"
// Map Value: updated below
const activeConnections = new Map();

// Chart Variables
let transformationChart = null;
let aggregationChart = null;

// --- Update Scheduling Flags ---
let transformationChartUpdatePending = false;
let aggregationChartUpdatePending = false;

// Analytics Counters (Global)
let rawSampleCount = 0;
let aggSummaryCount = 0;
let warningCount = 0;
let errorCount = 0;

// Color Palette for Sensor Lines
const sensorColors = [
  "rgb(255, 99, 132)",
  "rgb(54, 162, 235)",
  "rgb(75, 192, 192)",
  "rgb(255, 159, 64)",
  "rgb(153, 102, 255)",
  "rgb(255, 205, 86)",
  "rgb(201, 203, 207)",
  "rgb(231, 76, 60)",
  "rgb(46, 204, 113)",
  "rgb(52, 152, 219)",
  "rgb(155, 89, 182)",
  "rgb(241, 196, 15)",
];
let colorIndex = 0;

// Chart Configuration Defaults (mostly unchanged)
const defaultChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  animation: false, // Keep animations off for performance
  scales: {
    x: {
      ticks: {
        maxTicksLimit: 15, // Limit visible ticks
        font: { size: 9 },
        autoSkip: true, // Automatically skip labels to prevent overlap
        autoSkipPadding: 30, // Increase padding for skipping
      },
      title: { display: true, text: "Time", font: { size: 11 } },
    },
    y: {
      beginAtZero: false,
      ticks: { font: { size: 9 } },
      title: { display: true, text: "Value", font: { size: 11 } },
    },
  },
  plugins: {
    legend: {
      display: true,
      position: "top",
      labels: { font: { size: 10 }, boxWidth: 15 },
    },
    tooltip: { enabled: true, mode: "index", intersect: false }, // 'index' mode is good for time series
  },
  elements: {
    line: { tension: 0.1, borderWidth: 1.5 },
    point: { radius: 0, hoverRadius: 4, hitRadius: 10 },
  },
};
// Deep copy function
function deepCopy(obj) {
  return JSON.parse(JSON.stringify(obj));
}

// Function to safely parse float
function safeParseFloat(value) {
  const num = parseFloat(value);
  return isNaN(num) ? null : num;
}

// Get next color
function getNextColor() {
  const color = sensorColors[colorIndex % sensorColors.length];
  colorIndex++;
  return color;
}

// ----------------------------------------------------------------------------
// 0) Analytics & Status Update Functions
// ----------------------------------------------------------------------------
function updateLogAnalytics() {
  connectionCountSpan.textContent = activeConnections.size; // Update connection count
  rawCountSpan.textContent = rawSampleCount;
  aggCountSpan.textContent = aggSummaryCount;
  warnCountSpan.textContent = warningCount;
  errorCountSpan.textContent = errorCount;
}

function updateOverallStatus() {
  let connectedCount = 0;
  let connectingCount = 0;
  let errorCount = 0;
  activeConnections.forEach((conn) => {
    if (conn.status === "connected") connectedCount++;
    else if (conn.status === "connecting") connectingCount++;
    else if (conn.status === "error") errorCount++;
  });

  let statusText = "Disconnected";
  let statusClass = "bg-secondary";

  if (activeConnections.size > 0) {
    if (connectingCount > 0) {
      statusText = `Connecting (${connectingCount})... Connected (${connectedCount})`;
      statusClass = "bg-warning";
    } else if (errorCount > 0) {
      statusText = `Error (${errorCount}). Connected (${connectedCount})`;
      statusClass = "bg-danger";
    } else if (connectedCount > 0) {
      statusText = `Connected (${connectedCount} Sensors)`;
      statusClass = "bg-success";
    } else {
      statusText = "No Active Connections";
      statusClass = "bg-secondary";
    }
  }

  wsStatusSpan.textContent = statusText;
  wsStatusSpan.className = wsStatusSpan.className.replace(/bg-\S+/g, "");
  wsStatusSpan.classList.add(statusClass);
  updateLogAnalytics(); // Also update detailed counts
}

// ----------------------------------------------------------------------------
// 1) Chart Initialization (Start Empty)
// ----------------------------------------------------------------------------
function initTransformationChart() {
  const ctx = document.getElementById("transformationChart").getContext("2d");
  const options = deepCopy(defaultChartOptions);
  options.scales.y.title.text = "acc_z Value";
  options.animation = false;

  transformationChart = new Chart(ctx, {
    type: "line",
    data: {
      labels: [], // Start with empty labels
      datasets: [], // Start with empty datasets
    },
    options: options,
  });
}

function initAggregationChart() {
  const ctx = document.getElementById("aggregationChart").getContext("2d");
  const options = deepCopy(defaultChartOptions);
  options.scales.y.title.text = "Workload";
  options.elements.point.radius = 1;
  options.animation = { duration: 250 }; // Keep mild animation here

  aggregationChart = new Chart(ctx, {
    type: "line",
    data: {
      labels: [], // Start with empty labels
      datasets: [], // Start with empty datasets
    },
    options: options,
  });
}

// ----------------------------------------------------------------------------
// 2) Chart Data Update Functions (Target specific dataset)
// ----------------------------------------------------------------------------
function scheduleChartUpdate(chart, isPendingFlagRef) {
  if (chart && !isPendingFlagRef.value) {
    isPendingFlagRef.value = true;
    requestAnimationFrame(() => {
      if (chart.ctx) {
        chart.update("none"); 
      }
      isPendingFlagRef.value = false;
    });
  }
}

function addDataToSpecificDataset(
  chart,
  datasetIndex,
  value,
  isPendingFlagRef
) {
  if (
    !chart ||
    datasetIndex === null ||
    datasetIndex < 0 ||
    datasetIndex >= chart.data.datasets.length
  ) {
    return;
  }

  const parsedValue = safeParseFloat(value);
  if (parsedValue === null) {
    return; // Don't add nulls
  }

  if (
    !chart.data ||
    !chart.data.datasets ||
    !chart.data.datasets[datasetIndex]
  ) {
    console.warn(
      `Chart or dataset ${datasetIndex} no longer exists for chart ${chart.canvas.id}`
    );
    return;
  }
  const currentData = chart.data.datasets[datasetIndex].data;

  currentData.push(parsedValue);

  let labelAdded = false;
  if (chart.data.labels.length < currentData.length) {
    const nowLabel = new Date().toLocaleTimeString("en-US", {
      hour12: false, // Use 24-hour format
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      fractionalSecondDigits: 1,
    });
    chart.data.labels.push(nowLabel);
    labelAdded = true;
  }

  while (chart.data.labels.length > MAX_POINTS) {
    chart.data.labels.shift(); 
    labelAdded = false; 

    chart.data.datasets.forEach((ds) => {
      if (ds.data.length > 0) {
        ds.data.shift();
      }
    });
  }
  chart.data.datasets.forEach((ds) => {
    while (ds.data.length > chart.data.labels.length) {
      ds.data.shift();
    }
  });

  scheduleChartUpdate(chart, isPendingFlagRef);
}

// ----------------------------------------------------------------------------
// 3) Session List Functions (Add Connect/Disconnect buttons)
// ----------------------------------------------------------------------------
async function refreshSessions() {
  refreshButton.disabled = true;
  refreshButton.innerHTML =
    '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Refreshing...';
  setSessionFeedback("Loading sessions...", false);
  sessionListDiv.innerHTML =
    '<div class="list-group-item">Loading sessions...</div>'; // Clear previous list
  try {
    const res = await fetch(`${API_URL}/sessions`);
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    const sessions = await res.json();
    displaySessions(sessions);
    setSessionFeedback("", false);
  } catch (err) {
    errorCount++;
    updateLogAnalytics();
    console.error("Error fetching sessions:", err);
    sessionListDiv.innerHTML = `<div class="list-group-item text-danger">Error loading sessions. ${err.message}</div>`;
    setSessionFeedback(`Error: ${err.message}`, true);
    appendMessage(`Session Fetch Error: ${err.message}`, "ERROR");
  } finally {
    refreshButton.disabled = false;
    refreshButton.innerHTML = '<i class="bi bi-arrow-clockwise"></i> Refresh';
  }
}

function setSessionFeedback(message, isError) {
  sessionListFeedback.textContent = message;
  sessionListFeedback.className = `text-muted mb-2 ${
    isError ? "text-danger" : "text-success"
  }`;
  sessionListFeedback.style.display = message ? "block" : "none";
}

function displaySessions(sessions) {
  sessionListDiv.innerHTML = ""; // Clear loading message or old list
  if (!sessions || sessions.length === 0) {
    sessionListDiv.innerHTML =
      '<div class="list-group-item text-muted">No active sessions found.</div>';
    setSessionFeedback("No active sessions found.", false);
    return;
  }
  sessions.forEach((session) => {
    const sensorId = session.sensor_id;
    const sensorSessionId = session.sensor_session_id;
    const connectionKey = `${sensorId}:${sensorSessionId}`;
    const sanitizedKey = sanitizeId(connectionKey);
    const isConnected = activeConnections.has(connectionKey);
    const connectionInfo = activeConnections.get(connectionKey);
    const status = connectionInfo ? connectionInfo.status : "disconnected";

    const startedAt = session.started_at
      ? new Date(session.started_at * 1000).toLocaleString()
      : "N/A";
    const count = session.count ?? "N/A";
    const sumWorkload =
      session.sum_workload !== undefined && session.sum_workload !== null
        ? parseFloat(session.sum_workload).toFixed(2)
        : "N/A";

    const item = document.createElement("div");
    item.id = `item-${sanitizedKey}`;
    item.className =
      "list-group-item list-group-item-action flex-column align-items-start";
    item.innerHTML = `
                <div class="d-flex w-100 justify-content-between">
                    <div>
                        <h6 class="mb-1">Sensor: ${sensorId}</h6>
                        <small class="text-muted">Session: ${sensorSessionId}</small>
                    </div>
                    <div class="session-actions text-end">
                         <span id="status-${sanitizedKey}" class="badge me-2 align-middle ${
      status === "connected"
        ? "bg-success"
        : status === "connecting"
        ? "bg-warning text-dark"
        : status === "error"
        ? "bg-danger"
        : "bg-light text-dark"
    }">${status}</span>
                         <button id="btn-connect-${sanitizedKey}" class="btn btn-sm btn-success" ${
      isConnected ? "disabled" : ""
    } title="Connect">
                             <i class="bi bi-play-fill"></i>
                         </button>
                         <button id="btn-disconnect-${sanitizedKey}" class="btn btn-sm btn-danger" ${
      !isConnected || status === "connecting" ? "disabled" : ""
    } title="Disconnect">
                             <i class="bi bi-stop-fill"></i>
                         </button>
                         <button id="btn-delete-${sanitizedKey}" class="btn btn-sm btn-outline-danger" title="Delete Session Data">
                             <i class="bi bi-trash3"></i>
                         </button>
                    </div>
                </div>
                <p class="mb-1 small mt-1">
                    Athlete Session: ${session.athlete_session_id || "N/A"} <br>
                    Started: ${startedAt} | Count: ${count} | Sum Workload: ${sumWorkload}
                </p>
            `;

    const connectBtn = item.querySelector(`#btn-connect-${sanitizedKey}`);
    const disconnectBtn = item.querySelector(`#btn-disconnect-${sanitizedKey}`);
    const deleteBtn = item.querySelector(`#btn-delete-${sanitizedKey}`);

    if (connectBtn) {
      connectBtn.onclick = () => {
        connectSensor(sensorId, sensorSessionId);
        connectBtn.disabled = true;
        if (disconnectBtn) disconnectBtn.disabled = false;
        if (deleteBtn) deleteBtn.disabled = false;
        updateSessionItemStatus(connectionKey, "connecting");
      };
    }
    if (disconnectBtn) {
      disconnectBtn.onclick = () => {
        disconnectSensor(sensorId, sensorSessionId);
        if (connectBtn) connectBtn.disabled = false;
        disconnectBtn.disabled = true;
        if (deleteBtn) deleteBtn.disabled = false; 
        updateSessionItemStatus(connectionKey, "disconnecting");
      };
    }

    if (deleteBtn) {
      deleteBtn.onclick = async () => {
        if (
          !confirm(
            `Are you sure you want to permanently delete session data for ${sensorId} (${sensorSessionId})? This cannot be undone.`
          )
        ) {
          return;
        }

        deleteBtn.disabled = true; 
        deleteBtn.innerHTML =
          '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>'; 

        appendMessage(
          `Attempting to delete session ${sensorSessionId} for ${sensorId}...`,
          "INFO",
          sensorId
        );

        if (activeConnections.has(connectionKey)) {
          disconnectSensor(sensorId, sensorSessionId);
        }

        const deleteUrl = `${API_URL}/sessions/${sensorId}/${sensorSessionId}`;
        try {
          const response = await fetch(deleteUrl, { method: "DELETE" });

          if (response.ok) {
            appendMessage(
              `Successfully deleted session ${sensorSessionId} data for ${sensorId}.`,
              "CONN",
              sensorId
            ); 
            const itemToRemove = document.getElementById(
              `item-${sanitizedKey}`
            );
            if (itemToRemove) {
              itemToRemove.remove();
            }
            if (activeConnections.has(connectionKey)) {
              handleSensorDisconnect(connectionKey, "disconnected"); 
            }
            updateOverallStatus(); 
          } else {
            const errorData = await response
              .json()
              .catch(() => ({ detail: "Unknown error" }));
            const errorMsg =
              errorData.detail || `Failed with status ${response.status}`;
            appendMessage(
              `Failed to delete session ${sensorSessionId}: ${errorMsg}`,
              "ERROR",
              sensorId
            );
            deleteBtn.disabled = false;
            deleteBtn.innerHTML = '<i class="bi bi-trash3"></i>';
          }
        } catch (err) {
          console.error("Delete session fetch error:", err);
          appendMessage(
            `Network or fetch error deleting session ${sensorSessionId}: ${err.message}`,
            "ERROR",
            sensorId
          );
          deleteBtn.disabled = false;
          deleteBtn.innerHTML = '<i class="bi bi-trash3"></i>';
        }
      };
    }

    sessionListDiv.appendChild(item);
  });
  setSessionFeedback(`Loaded ${sessions.length} sessions.`, false);
  updateOverallStatus();
}

function updateSessionItemStatus(connectionKey, status) {
  const sanitizedKey = sanitizeId(connectionKey);
  const statusBadge = document.getElementById(`status-${sanitizedKey}`);
  const connectBtn = document.getElementById(`btn-connect-${sanitizedKey}`);
  const disconnectBtn = document.getElementById(
    `btn-disconnect-${sanitizedKey}`
  );
  const deleteBtn = document.getElementById(`btn-delete-${sanitizedKey}`); 

  if (!statusBadge || !connectBtn || !disconnectBtn || !deleteBtn) {
    return;
  }

  let statusClass = "bg-light text-dark";
  let canConnect = true;
  let canDisconnect = false;
  let canDelete = true; 

  switch (status) {
    case "connected":
      statusClass = "bg-success";
      canConnect = false;
      canDisconnect = true;
      break;
    case "connecting":
      statusClass = "bg-warning text-dark";
      canConnect = false;
      canDisconnect = true;
      break; 
    case "error":
      statusClass = "bg-danger";
      canConnect = true;
      canDisconnect = false;
      break; 
    case "disconnecting":
      statusClass = "bg-warning text-dark";
      canConnect = false;
      canDisconnect = false;
      break; 
    case "disconnected":
    default:
      statusClass = "bg-light text-dark";
      canConnect = true;
      canDisconnect = false;
      status = "disconnected";
      break;
  }

  statusBadge.textContent = status;
  statusBadge.className = `badge me-2 align-middle ${statusClass}`;
  connectBtn.disabled = !canConnect;
  disconnectBtn.disabled = !canDisconnect;
  deleteBtn.disabled = !canDelete || status === "disconnecting"; 
}
// ----------------------------------------------------------------------------
// 4) WebSocket Connection & Message Handling (Per Sensor)
// ----------------------------------------------------------------------------
function appendMessage(msg, type = "INFO", sensorId = null, data = null) {
  if (!messagesDiv) return;
  if (
    messagesDiv.children.length === 1 &&
    messagesDiv.firstElementChild.classList.contains("text-muted")
  ) {
    messagesDiv.innerHTML = ""; // Clear initial message
  }
  const p = document.createElement("p");
  let icon = "",
    typeClass = "";
  switch (
    type 
  ) {
    case "RAW":
      icon = '<i class="bi bi-cpu text-info"></i> ';
      typeClass = "text-info-emphasis";
      break;
    case "AGG":
      icon = '<i class="bi bi-calculator text-primary"></i> ';
      typeClass = "text-primary-emphasis";
      break;
    case "CONN":
      icon = '<i class="bi bi-check-circle text-success"></i> ';
      typeClass = "text-success-emphasis";
      break;
    case "DISCONN":
      icon = '<i class="bi bi-x-circle text-warning"></i> ';
      typeClass = "text-warning-emphasis";
      break;
    case "ERROR":
      icon = '<i class="bi bi-exclamation-triangle text-danger"></i> ';
      typeClass = "text-danger-emphasis";
      break;
    case "WARN":
      icon = '<i class="bi bi-exclamation-circle text-warning"></i> ';
      typeClass = "text-warning-emphasis";
      break;
    default:
      icon = '<i class="bi bi-info-circle"></i> ';
      typeClass = "text-body";
  }
  const safeMsg = String(msg).replace(/</g, "<").replace(/>/g, ">"); // Basic sanitization
  let details = "";
  if ((type === "ERROR" || type === "WARN") && data) {
    details = `<br><small class="text-muted">Data: ${JSON.stringify(data)
      .replace(/</g, "<")
      .replace(/>/g, ">")}</small>`;
  }
  const prefix = sensorId ? `[${sensorId}] ` : "";
  p.innerHTML = `${icon}<span class="${typeClass}">${type}</span> [${new Date().toLocaleTimeString()}] ${prefix}${safeMsg}${details}`;
  messagesDiv.appendChild(p);
  if (
    messagesDiv.scrollHeight -
      messagesDiv.scrollTop -
      messagesDiv.clientHeight <
    100
  ) {
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
  }
}

// --- Connect a specific sensor ---
function connectSensor(sensorId, sensorSessionId) {
  const connectionKey = `${sensorId}:${sensorSessionId}`;
  if (activeConnections.has(connectionKey)) {
    appendMessage(
      `Already connected or connecting to ${sensorId}`,
      "WARN",
      sensorId
    );
    return;
  }

  appendMessage(`Attempting connection for ${sensorId}...`, "INFO", sensorId);

  // Add datasets to charts
  const color = getNextColor();
  const transformDataset = {
    label: `${sensorId} acc_z`,
    data: [],
    borderColor: color,
    backgroundColor: color + "80", 
    fill: false,
    hidden: false, 
  };
  transformationChart.data.datasets.push(transformDataset);
  const transformDatasetIndex = transformationChart.data.datasets.length - 1;

  const aggSumDataset = {
    label: `${sensorId} Sum WL`,
    data: [],
    borderColor: color,
    fill: true,
    hidden: false,
  };
  aggregationChart.data.datasets.push(aggSumDataset);
  const aggSumDatasetIndex = aggregationChart.data.datasets.length - 1;

  transformationChart.update();
  aggregationChart.update();

  // Store connection details
  const connectionInfo = {
    rawWs: null,
    aggWs: null,
    status: "connecting",
    transformDatasetIndex: transformDatasetIndex,
    aggSumDatasetIndex: aggSumDatasetIndex,
    color: color,
    rawConnected: false, 
    aggConnected: false,
    // --- NEW: Initialize ping intervals as null ---
    rawPingInterval: null,
    aggPingInterval: null
  };
  activeConnections.set(connectionKey, connectionInfo);
  updateSessionItemStatus(connectionKey, "connecting"); 
  updateOverallStatus(); 

  // --- Connect to RAW WebSocket ---
  const wsRawUrl = `${WS_URL_BASE}/raw/${sensorId}`;
  appendMessage(`Connecting to RAW: ${wsRawUrl}`, "INFO", sensorId);
  const rawWs = new WebSocket(wsRawUrl);
  connectionInfo.rawWs = rawWs;

  rawWs.onopen = () => {
    appendMessage("Connected to RAW WebSocket.", "CONN", sensorId);
    connectionInfo.rawConnected = true;
    checkSensorConnected(connectionKey);
    
    // --- NEW: Start the ping interval for RAW ---
    // Clear any existing interval just in case
    if (connectionInfo.rawPingInterval) clearInterval(connectionInfo.rawPingInterval);
    connectionInfo.rawPingInterval = setInterval(() => {
        if (rawWs.readyState === WebSocket.OPEN) {
            rawWs.send("ping"); // Send the keep-alive message
        }
    }, PING_INTERVAL_MS);
  };
  rawWs.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      const processSample = (sample) => {
        rawSampleCount++; 
        if ("acc_z" in sample) {
          const az = safeParseFloat(sample.acc_z);
          if (az !== null) {
            const pendingRef = {
              get value() {
                return transformationChartUpdatePending;
              },
              set value(v) {
                transformationChartUpdatePending = v;
              },
            };
            addDataToSpecificDataset(
              transformationChart,
              connectionInfo.transformDatasetIndex,
              az,
              pendingRef
            );
          } else {
            warningCount++;
            appendMessage(
              `RAW sample ignored (invalid acc_z)`,
              "WARN",
              sensorId,
              sample
            );
          }
        } else {
          warningCount++;
          appendMessage(
            `RAW sample ignored (missing acc_z)`,
            "WARN",
            sensorId,
            sample
          );
        }
      };

      if (
        data.type === "transformed-data" &&
        data.samples &&
        Array.isArray(data.samples)
      ) {
        appendMessage(
          `Received RAW batch (${data.samples.length})`,
          "RAW",
          sensorId
        ); 
        data.samples.forEach(processSample);
      } else if ("acc_z" in data) {
        processSample(data);
      } else {
        // Could be the server's welcome message asking for a ping
        if (data.message && data.message.includes("keep-alive")) {
            appendMessage(data.message, "INFO", sensorId);
        } else {
            warningCount++;
            appendMessage(
                `RAW message ignored: unrecognized format`,
                "WARN",
                sensorId,
                data
            );
        }
      }
      updateLogAnalytics(); 
    } catch (err) {
      errorCount++;
      updateLogAnalytics();
      console.error(
        `[${sensorId}] RAW message processing error:`,
        err,
        "Raw data:",
        event.data
      );
      appendMessage(
        `Error processing RAW message: ${err.message}`,
        "ERROR",
        sensorId,
        { rawData: event.data }
      );
    }
  };
  rawWs.onclose = (event) => {
    appendMessage(
      `RAW WebSocket disconnected. Code: ${event.code}`,
      "DISCONN",
      sensorId
    );
    connectionInfo.rawConnected = false;
    handleSensorDisconnect(connectionKey, "disconnected");
  };
  rawWs.onerror = (event) => {
    errorCount++;
    updateLogAnalytics();
    console.error(`[${sensorId}] RAW WebSocket error:`, event);
    appendMessage("RAW WebSocket error.", "ERROR", sensorId);
    connectionInfo.rawConnected = false;
    if (rawWs && rawWs.readyState !== WebSocket.CLOSED) rawWs.close();
    handleSensorDisconnect(connectionKey, "error"); 
  };

  // --- Connect to AGGREGATE WebSocket ---
  const wsAggUrl = `${WS_URL_BASE}/aggregate/${sensorId}`;
  appendMessage(`Connecting to AGG: ${wsAggUrl}`, "INFO", sensorId);
  const aggWs = new WebSocket(wsAggUrl);
  connectionInfo.aggWs = aggWs;

  aggWs.onopen = () => {
    appendMessage("Connected to AGGREGATE WebSocket.", "CONN", sensorId);
    connectionInfo.aggConnected = true;
    checkSensorConnected(connectionKey);

    // --- NEW: Start the ping interval for AGGREGATE ---
    // Clear any existing interval just in case
    if (connectionInfo.aggPingInterval) clearInterval(connectionInfo.aggPingInterval);
    connectionInfo.aggPingInterval = setInterval(() => {
        if (aggWs.readyState === WebSocket.OPEN) {
            aggWs.send("ping"); // Send the keep-alive message
        }
    }, PING_INTERVAL_MS);
  };
  aggWs.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      aggSummaryCount++; 

      if (
        data.type === "batch_summary" &&
        "sum_workload" in data &&
        "last_workload" in data
      ) {
        const sumWl = safeParseFloat(data.sum_workload);
        const lastWl = safeParseFloat(data.last_workload);
        if (sumWl !== null && lastWl !== null) {
          const pendingRef = {
            get value() {
              return aggregationChartUpdatePending;
            },
            set value(v) {
              aggregationChartUpdatePending = v;
            },
          };
          addDataToSpecificDataset(
            aggregationChart,
            connectionInfo.aggSumDatasetIndex,
            sumWl,
            pendingRef
          );
        } else {
          warningCount++;
          appendMessage(
            `AGG summary ignored (invalid values)`,
            "WARN",
            sensorId,
            data
          );
        }
      } else {
         // Could be the server's welcome message asking for a ping
         if (data.message && data.message.includes("keep-alive")) {
            appendMessage(data.message, "INFO", sensorId);
        } else {
            warningCount++;
            appendMessage(
            `AGG data ignored (unexpected format/keys)`,
            "WARN",
            sensorId,
            data
            );
        }
      }
      updateLogAnalytics(); 
    } catch (err) {
      errorCount++;
      updateLogAnalytics();
      console.error(
        `[${sensorId}] AGG message processing error:`,
        err,
        "Raw data:",
        event.data
      );
      appendMessage(
        `Error processing AGG message: ${err.message}`,
        "ERROR",
        sensorId,
        { rawData: event.data }
      );
    }
  };
  aggWs.onclose = (event) => {
    appendMessage(
      `AGG WebSocket disconnected. Code: ${event.code}`,
      "DISCONN",
      sensorId
    );
    connectionInfo.aggConnected = false;
    handleSensorDisconnect(connectionKey, "disconnected");
  };
  aggWs.onerror = (event) => {
    errorCount++;
    updateLogAnalytics();
    console.error(`[${sensorId}] AGG WebSocket error:`, event);
    appendMessage("AGG WebSocket error.", "ERROR", sensorId);
    connectionInfo.aggConnected = false;
    if (aggWs && aggWs.readyState !== WebSocket.CLOSED) aggWs.close();
    handleSensorDisconnect(connectionKey, "error"); 
  };
}

// --- Check if both WS for a sensor are connected ---
function checkSensorConnected(connectionKey) {
  const connectionInfo = activeConnections.get(connectionKey);
  if (!connectionInfo) return;

  if (
    connectionInfo.rawConnected &&
    connectionInfo.aggConnected &&
    connectionInfo.status !== "connected"
  ) {
    connectionInfo.status = "connected";
    appendMessage(
      `Fully connected for ${connectionKey.split(":")[0]}`,
      "CONN",
      connectionKey.split(":")[0]
    );
    updateSessionItemStatus(connectionKey, "connected");
    updateOverallStatus();
  } else if (connectionInfo.rawConnected || connectionInfo.aggConnected) {
    if (connectionInfo.status !== "connecting") {
      connectionInfo.status = "connecting";
      appendMessage(
        `Partially connected for ${connectionKey.split(":")[0]}`,
        "WARN",
        connectionKey.split(":")[0]
      );
      updateSessionItemStatus(connectionKey, "connecting");
      updateOverallStatus();
    }
  }
}

// --- Disconnect a specific sensor ---
function disconnectSensor(sensorId, sensorSessionId) {
  const connectionKey = `${sensorId}:${sensorSessionId}`;
  const connectionInfo = activeConnections.get(connectionKey);

  if (!connectionInfo) {
    appendMessage(`Not connected to ${sensorId}`, "WARN", sensorId);
    return;
  }

  appendMessage(
    `Manual disconnect requested for ${sensorId}`,
    "INFO",
    sensorId
  );
  updateSessionItemStatus(connectionKey, "disconnecting");

  connectionInfo.rawWs?.close();
  connectionInfo.aggWs?.close();
}

// --- Handle cleanup when a sensor disconnects or errors ---
function handleSensorDisconnect(connectionKey, finalStatus) {
  const connectionInfo = activeConnections.get(connectionKey);
  if (!connectionInfo) return;

  const rawClosed =
    !connectionInfo.rawWs ||
    connectionInfo.rawWs.readyState >= WebSocket.CLOSING;
  const aggClosed =
    !connectionInfo.aggWs ||
    connectionInfo.aggWs.readyState >= WebSocket.CLOSING;

  if ((rawClosed && aggClosed) || finalStatus === "error") {
    connectionInfo.status = finalStatus;
    try {
      if (
        connectionInfo.transformDatasetIndex !== null &&
        transformationChart?.data?.datasets[
          connectionInfo.transformDatasetIndex
        ]
      ) {
        transformationChart.getDatasetMeta(
          connectionInfo.transformDatasetIndex
        ).hidden = true;
      }
      if (
        connectionInfo.aggSumDatasetIndex !== null &&
        aggregationChart?.data?.datasets[connectionInfo.aggSumDatasetIndex]
      ) {
        aggregationChart.getDatasetMeta(
          connectionInfo.aggSumDatasetIndex
        ).hidden = true;
      }
      if (transformationChart?.ctx) transformationChart.update("none");
      if (aggregationChart?.ctx) aggregationChart.update("none");
    } catch (chartError) {
      console.warn(
        "Error hiding chart datasets during disconnect, chart might be gone:",
        chartError
      );
    }

    // --- NEW: Clear the ping intervals to prevent memory leaks ---
    if (connectionInfo.rawPingInterval) {
        clearInterval(connectionInfo.rawPingInterval);
        connectionInfo.rawPingInterval = null;
    }
    if (connectionInfo.aggPingInterval) {
        clearInterval(connectionInfo.aggPingInterval);
        connectionInfo.aggPingInterval = null;
    }

    activeConnections.delete(connectionKey);
    const sensorId = connectionKey.split(":")[0];
    const sanitizedKey = sanitizeId(connectionKey);
    const itemElement = document.getElementById(`item-${sanitizedKey}`);
    if (itemElement) {
      updateSessionItemStatus(
        connectionKey,
        finalStatus === "error" ? "error" : "disconnected"
      );
    }
    updateOverallStatus();

    scheduleChartUpdate(transformationChart, {
      get value() {
        return transformationChartUpdatePending;
      },
      set value(v) {
        transformationChartUpdatePending = v;
      },
    });
    scheduleChartUpdate(aggregationChart, {
      get value() {
        return aggregationChartUpdatePending;
      },
      set value(v) {
        aggregationChartUpdatePending = v;
      },
    });
  } else {
    if (connectionInfo.status !== "error") {
      connectionInfo.status = "disconnecting";
      const sanitizedKey = sanitizeId(connectionKey);
      const itemElement = document.getElementById(`item-${sanitizedKey}`);
      if (itemElement) {
        updateSessionItemStatus(connectionKey, "disconnecting");
      }
      updateOverallStatus();
    }
  }
}
// ----------------------------------------------------------------------------
// 5) Initialization
// ----------------------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
  refreshButton.addEventListener("click", refreshSessions);
  initTransformationChart();
  initAggregationChart();
  updateLogAnalytics(); // Init counters
  refreshSessions(); // Fetch initial sessions
  updateOverallStatus(); // Set initial status text
});