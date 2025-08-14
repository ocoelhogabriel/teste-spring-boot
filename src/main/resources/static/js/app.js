document.addEventListener('DOMContentLoaded', function() {
	// DOM Elements
	const logContent = document.getElementById('log-content');
	const logFilesList = document.getElementById('log-files-list');
	const toggleLiveBtn = document.getElementById('toggle-live');
	const refreshLogsBtn = document.getElementById('refresh-logs');
	const downloadLogBtn = document.getElementById('download-log');
	const clearLogsBtn = document.getElementById('clear-logs');
	const logLevelSelect = document.getElementById('log-level');
	const logSearch = document.getElementById('log-search');
	const logDate = document.getElementById('log-date');
	const logFileInfo = document.getElementById('log-file-info');
	const logStatus = document.getElementById('log-status');
	const lineCount = document.getElementById('line-count');

	// State
	let currentLogFile = null;
	let liveLogsEnabled = false;
	let eventSource = null;
	let logsData = [];
	let filteredLogs = [];

	// Initialize
	loadLogFiles();
	setupEventListeners();

	function setupEventListeners() {
		toggleLiveBtn.addEventListener('click', toggleLiveLogs);
		refreshLogsBtn.addEventListener('click', loadLogFiles);
		downloadLogBtn.addEventListener('click', downloadCurrentLog);
		clearLogsBtn.addEventListener('click', clearLogViewer);
		logLevelSelect.addEventListener('change', filterLogs);
		logSearch.addEventListener('input', filterLogs);
		logDate.addEventListener('change', filterByDate);
	}

	async function loadLogFiles() {
		try {
			updateStatus('Loading log files...');
			const response = await fetch('/api/logs/files');
			if (!response.ok) throw new Error('Failed to load log files');

			const files = await response.json();
			renderLogFiles(files);
			updateStatus('Log files loaded');
		} catch (error) {
			console.error('Error loading log files:', error);
			updateStatus('Error loading log files', 'error');
		}
	}

	function renderLogFiles(files) {
		logFilesList.innerHTML = '';
		files.forEach(file => {
			const li = document.createElement('li');
			li.textContent = file.name;
			li.dataset.file = file.name;
			li.addEventListener('click', () => loadLogFile(file.name));
			logFilesList.appendChild(li);
		});
	}

	async function loadLogFile(filename) {
		try {
			updateStatus(`Loading ${filename}...`);
			currentLogFile = filename;
			logFileInfo.textContent = filename;

			// Highlight selected file
			document.querySelectorAll('#log-files-list li').forEach(li => {
				li.classList.toggle('active', li.dataset.file === filename);
			});

			const response = await fetch(`/api/logs/file?name=${encodeURIComponent(filename)}`);
			if (!response.ok) throw new Error('Failed to load log file');

			const text = await response.text();
			logsData = text.split('\n');
			renderLogs(logsData);
			updateStatus(`${filename} loaded`);
			downloadLogBtn.disabled = false;
		} catch (error) {
			console.error('Error loading log file:', error);
			updateStatus(`Error loading ${filename}`, 'error');
		}
	}

	function renderLogs(logLines) {
		logContent.innerHTML = '';
		let count = 0;

		logLines.forEach(line => {
			if (!line.trim()) return;

			const div = document.createElement('div');
			div.className = 'log-line';

			// Extract log level for styling
			const levelMatch = line.match(/\[(INFO|WARN|ERROR|DEBUG|TRACE)\]/);
			if (levelMatch) {
				const level = levelMatch[1];
				div.classList.add(`log-level-${level}`);
			}

			div.textContent = line;
			logContent.appendChild(div);
			count++;
		});

		lineCount.textContent = `${count} lines`;
		filteredLogs = logLines;
	}

	function filterLogs() {
		if (!logsData.length && !liveLogsEnabled) return;

		const level = logLevelSelect.value;
		const searchTerm = logSearch.value.toLowerCase();

		// Se estiver em modo live, filtra apenas o que está sendo exibido
		if (liveLogsEnabled) {
			const logLines = Array.from(logContent.children).map(div => div.textContent);
			filteredLogs = logLines.filter(line => {
				const matchesLevel = level === 'ALL' || line.includes(`[${level}]`);
				const matchesSearch = !searchTerm || line.toLowerCase().includes(searchTerm);
				return matchesLevel && matchesSearch;
			});

			// Re-renderiza apenas o conteúdo filtrado
			logContent.innerHTML = '';
			filteredLogs.forEach(line => {
				const div = document.createElement('div');
				div.className = 'log-line';
				if (line.match(/\[(INFO|WARN|ERROR|DEBUG|TRACE)\]/)) {
					div.classList.add(`log-level-${levelMatch[1]}`);
				}
				div.textContent = line;
				logContent.appendChild(div);
			});
		} else {
			// Filtra o conjunto completo de logs
			filteredLogs = logsData.filter(line => {
				const matchesLevel = level === 'ALL' || line.includes(`[${level}]`);
				const matchesSearch = !searchTerm || line.toLowerCase().includes(searchTerm);
				return matchesLevel && matchesSearch;
			});
			renderLogs(filteredLogs);
		}
	}

	function filterByDate() {
		if (!logsData.length || !logDate.value) return;

		const selectedDate = new Date(logDate.value);
		const dateString = selectedDate.toISOString().split('T')[0];

		filteredLogs = logsData.filter(line => {
			return line.includes(dateString);
		});

		renderLogs(filteredLogs);
	}

	function toggleLiveLogs() {
		liveLogsEnabled = !liveLogsEnabled;

		if (liveLogsEnabled) {
			startLiveLogs();
			toggleLiveBtn.innerHTML = '<i class="fas fa-stop"></i> Stop Live';
			toggleLiveBtn.classList.remove('btn-primary');
			toggleLiveBtn.classList.add('btn-danger');
		} else {
			stopLiveLogs();
			toggleLiveBtn.innerHTML = '<i class="fas fa-play"></i> Live Logs';
			toggleLiveBtn.classList.remove('btn-danger');
			toggleLiveBtn.classList.add('btn-primary');
		}
	}

	function startLiveLogs() {
		if (eventSource) eventSource.close();

		// Adicione o arquivo atual como parâmetro se estiver selecionado
		const fileParam = currentLogFile ? `?file=${encodeURIComponent(currentLogFile)}` : '';
		const levelParam = logLevelSelect.value !== 'ALL' ? `${fileParam ? '&' : '?'}level=${logLevelSelect.value}` : '';

		eventSource = new EventSource(`/api/logs/stream${fileParam}${levelParam}`);

		eventSource.onmessage = function(event) {
			const logLine = event.data;
			addLogLine(logLine);

			// Atualiza automaticamente o contador de linhas
			const currentCount = parseInt(lineCount.textContent.split(' ')[0]) || 0;
			lineCount.textContent = `${currentCount + 1} lines`;
		};

		eventSource.onerror = function() {
			console.error('Error in SSE connection');
			updateStatus('Connection to server lost', 'error');
			stopLiveLogs();
		};
	}

	function addLogLine(line) {
		if (!line.trim()) return;

		const div = document.createElement('div');
		div.className = 'log-line';

		// Extrai o nível do log para estilização
		const levelMatch = line.match(/\[(INFO|WARN|ERROR|DEBUG|TRACE)\]/);
		if (levelMatch) {
			const level = levelMatch[1];
			div.classList.add(`log-level-${level}`);
		}

		div.textContent = line;

		// Adiciona no topo (para comportamento de "últimos primeiro")
		logContent.insertBefore(div, logContent.firstChild);

		// Mantém um limite máximo de linhas para performance
		if (logContent.children.length > 1000) {
			logContent.removeChild(logContent.lastChild);
		}
	}


	function stopLiveLogs() {
		if (eventSource) {
			eventSource.close();
			eventSource = null;
		}
	}

	function downloadCurrentLog() {
		if (!currentLogFile || !filteredLogs.length) return;

		const blob = new Blob([filteredLogs.join('\n')], { type: 'text/plain' });
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = currentLogFile.replace('.log', '_filtered.log') || 'logs.txt';
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
		URL.revokeObjectURL(url);
	}

	function clearLogViewer() {
		logContent.innerHTML = '';
		logsData = [];
		filteredLogs = [];
		lineCount.textContent = '0 lines';
		logFileInfo.textContent = 'No file selected';
		downloadLogBtn.disabled = true;

		document.querySelectorAll('#log-files-list li').forEach(li => {
			li.classList.remove('active');
		});

		currentLogFile = null;
		updateStatus('Log viewer cleared');
	}

	function updateStatus(message, type = 'info') {
		logStatus.textContent = message;
		logStatus.className = '';

		if (type === 'error') {
			logStatus.classList.add('error');
		} else if (type === 'success') {
			logStatus.classList.add('success');
		}
	}
});