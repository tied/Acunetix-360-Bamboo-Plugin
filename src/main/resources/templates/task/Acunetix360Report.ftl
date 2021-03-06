<div style="padding: 20px">
    <div class="paddedClearer"></div>
    <div style="color: #3f3f3f;display:inline;font-size: 130%;">
        <img src="${req.contextPath}/download/resources/com.acunetix.acunetix360-bamboo-plugin:acunetix-360-assets/acunetix-360-logo.svg"
             alt="Acunetix 360"
             style="vertical-align:top; margin-bottom:1px;display: inline-block;height:1.6em;width: auto;"/>
        <h1 style="zoom:1;color: #3f3f3f;display:inline-block">Acunetix 360 Report</h1>
    </div>
    <div class="aui-page-panel">
        <div class="aui-page-panel-inner">
            <section class="aui-page-panel-content">
                <div>
                    <p id="acunetixScanResultWarning"></p>
                    <div id="acunetixScanResultContainer" style="display:none">
                        <iframe id="acunetixScanResult" style="width:100%;height:70vh;"></iframe>
                    </div>

                    <script>
                        var isReportGenerated =  ${IsReportGenerated};
                        var content;
                        var hasError =${hasError};
                        var errorMessage = "${errorMessage}";
                        var warning = document.getElementById("acunetixScanResultWarning");
                        var iframeContainer = document.getElementById('acunetixScanResultContainer');
                        var iframe = document.getElementById('acunetixScanResult');

                        if (hasError) {
                            warning.textContent = errorMessage;
                            hideElement(iframe);
                            showElement(warning);
                        } else {
                            var requestURL = "${req.contextPath}/rest/acunetixBambooApi/1.0/report/${scanTaskID}";

                            var xhr = new XMLHttpRequest();
                            xhr.open("GET", requestURL);
                            xhr.onload = function () {
                                if (xhr.status === 200) {
                                    content = xhr.responseText;
                                    if (isReportGenerated) {
                                        iframe = iframe.contentWindow || (iframe.contentDocument.document || iframe.contentDocument);
                                        iframe.document.open();
                                        iframe.document.write(content);
                                        iframe.document.close();
                                        showElement(iframeContainer);
                                        hideElement(warning);
                                    } else {
                                        warning.textContent = content;
                                        hideElement(iframeContainer);
                                        showElement(warning);
                                    }
                                } else {
                                    warning.textContent = "Something went wrong.";
                                    hideElement(iframeContainer);
                                    showElement(warning);
                                }
                            };
                            xhr.send();
                        }

                        function showElement(elem) {
                            elem.style.display = 'block';
                        };

                        function hideElement(elem) {
                            elem.style.display = 'none';
                        };

                    </script>
                </div>
            </section>
        </div>
    </div>
    <br>
</div>