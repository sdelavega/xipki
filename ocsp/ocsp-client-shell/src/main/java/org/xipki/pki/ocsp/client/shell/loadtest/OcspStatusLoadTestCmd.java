/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.pki.ocsp.client.shell.loadtest;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.commons.console.karaf.IllegalCmdParamException;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ocsp.client.api.RequestOptions;
import org.xipki.pki.ocsp.client.shell.OcspStatusCommandSupport;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-ocsp", name = "loadtest-status",
        description = "OCSP Load test")
@Service
public class OcspStatusLoadTestCmd extends OcspStatusCommandSupport {
    @Option(name = "--serial",
            multiValued = true,
            description = "serial number\n"
                    + "(multi-valued, at least one of serial and cert must be specified)")
    private List<String> serialNumberList;

    @Option(name = "--cert",
            multiValued = true,
            description = "certificate\n"
                    + "(multi-valued)")
    @Completion(FilePathCompleter.class)
    private List<String> certFiles;

    @Option(name = "--duration",
            description = "duration in seconds")
    private int durationInSecond = 30;

    @Option(name = "--thread",
            description = "number of threads")
    private Integer numThreads = 5;

    @Option(name = "--url",
            required = true,
            description = "OCSP responder URL\n"
                    + "required")
    private String serverUrlS;

    @Override
    protected Object doExecute() throws Exception {
        List<BigInteger> serialNumbers = new LinkedList<>();

        if (serialNumberList != null) {
            for (String serialNumber : serialNumberList) {
                try {
                    serialNumbers.add(toBigInt(serialNumber));
                } catch (Exception ex) {
                    throw new IllegalCmdParamException(
                            "invalid serial number '" + serialNumber + "'", ex);
                }
            }
        }

        if (certFiles != null) {
            for (String certFile : certFiles) {
                X509Certificate cert;
                try {
                    cert = X509Util.parseCert(certFile);
                } catch (Exception ex) {
                    throw new IllegalCmdParamException(
                            "invalid certificate file  '" + certFile + "'", ex);
                }
                serialNumbers.add(cert.getSerialNumber());
            }
        }

        if (numThreads < 1) {
            throw new IllegalCmdParamException("invalid number of threads " + numThreads);
        }

        URL serverUrl;
        try {
            serverUrl = new URL(serverUrlS);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("invalid URL: " + serverUrlS);
        }

        StringBuilder description = new StringBuilder();
        description.append("issuer cert: ").append(issuerCertFile).append("\n");
        description.append("server URL: ").append(serverUrl.toString()).append("\n");
        description.append("hash: ").append(hashAlgo);

        X509Certificate issuerCert = X509Util.parseCert(issuerCertFile);

        RequestOptions options = getRequestOptions();

        OcspLoadTest loadTest = new OcspLoadTest(requestor, serialNumbers,
                issuerCert, serverUrl, options, description.toString());
        loadTest.setDuration(durationInSecond);
        loadTest.setThreads(numThreads);
        loadTest.test();

        return null;
    } // end doExecute

}
