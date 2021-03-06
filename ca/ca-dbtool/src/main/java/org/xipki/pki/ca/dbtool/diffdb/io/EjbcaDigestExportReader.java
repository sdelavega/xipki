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

package org.xipki.pki.ca.dbtool.diffdb.io;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.datasource.DataSourceWrapper;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.pki.ca.dbtool.DbToolBase;
import org.xipki.pki.ca.dbtool.IdRange;
import org.xipki.pki.ca.dbtool.diffdb.EjbcaConstants;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class EjbcaDigestExportReader {

    private class Retriever implements Runnable {

        private Connection conn;

        private PreparedStatement selectCertStmt;

        private PreparedStatement selectRawCertStmt;

        Retriever() throws DataAccessException {
            this.conn = datasource.getConnection();
            try {
                selectCertStmt = datasource.prepareStatement(conn, selectCertSql);
                selectRawCertStmt = datasource.prepareStatement(conn, selectRawCertSql);
            } catch (DataAccessException ex) {
                releaseResources(selectCertStmt, null);
                releaseResources(selectRawCertStmt, null);
                datasource.returnConnection(conn);
                throw ex;
            }
        }

        @Override
        public void run() {
            while (!stop.get()) {
                try {
                    IdRange idRange = inQueue.take();
                    query(idRange);
                } catch (InterruptedException ex) {
                    LOG.error("InterruptedException: {}", ex.getMessage());
                }
            }

            releaseResources(selectCertStmt, null);
            datasource.returnConnection(conn);
            selectCertStmt = null;
        }

        private void query(final IdRange idRange) {
            DigestDbEntrySet result = new DigestDbEntrySet(idRange.getFrom());

            ResultSet rs = null;
            try {
                selectCertStmt.setInt(1, idRange.getFrom());
                selectCertStmt.setInt(2, idRange.getTo() + 1);

                rs = selectCertStmt.executeQuery();

                int id;
                String hexCaFp;
                String hexCertFp;

                while (rs.next()) {
                    id = rs.getInt("id");
                    hexCaFp = rs.getString("cAFingerprint");
                    hexCertFp = rs.getString("fingerprint");

                    EjbcaCaInfo caInfo = null;

                    if (!hexCaFp.equals(hexCertFp)) {
                        caInfo = fpCaInfoMap.get(hexCaFp);
                    }

                    if (caInfo == null) {
                        LOG.debug("Found no CA by caFingerprint, try to resolve by issuer");
                        selectRawCertStmt.setInt(1, id);

                        ResultSet certRs = selectRawCertStmt.executeQuery();

                        if (certRs.next()) {
                            String b64Cert = certRs.getString("base64Cert");
                            Certificate cert = Certificate.getInstance(Base64.decode(b64Cert));
                            for (EjbcaCaInfo entry : fpCaInfoMap.values()) {
                                if (entry.getSubject().equals(cert.getIssuer())) {
                                    caInfo = entry;
                                    break;
                                }
                            }
                        }
                        certRs.close();
                    }

                    if (caInfo == null) {
                        LOG.error("FOUND no CA for Cert with id '{}'", id);
                        numSkippedCerts.incrementAndGet();
                        continue;
                    }

                    String hash = Base64.toBase64String(Hex.decode(hexCertFp));

                    String str = rs.getString("serialNumber");
                    BigInteger serial = new BigInteger(str); // decimal

                    int status = rs.getInt("status");
                    boolean revoked = (status == EjbcaConstants.CERT_REVOKED
                            || status == EjbcaConstants.CERT_TEMP_REVOKED);

                    Integer revReason = null;
                    Long revTime = null;
                    Long revInvTime = null;

                    if (revoked) {
                        revReason = rs.getInt("revocationReason");
                        long revTimeInMs = rs.getLong("revocationDate");
                        // rev_time is milliseconds, convert it to seconds
                        revTime = revTimeInMs / 1000;
                    }

                    DbDigestEntry cert = new DbDigestEntry(serial, revoked, revReason, revTime,
                            revInvTime, hash);

                    IdentifiedDbDigestEntry idCert = new IdentifiedDbDigestEntry(cert, id);
                    idCert.setCaId(caInfo.getCaId());

                    result.addEntry(idCert);
                }
            } catch (Exception ex) {
                if (ex instanceof SQLException) {
                    ex = datasource.translate(selectCertSql, (SQLException) ex);
                }
                result.setException(ex);
            } finally {
                outQueue.add(result);
                releaseResources(null, rs);
            }
        } // method query

    } // class Retriever

    private static final Logger LOG = LoggerFactory.getLogger(EjbcaDigestExportReader.class);

    protected final AtomicBoolean stop = new AtomicBoolean(false);

    protected final BlockingDeque<IdRange> inQueue = new LinkedBlockingDeque<>();

    protected final BlockingDeque<DigestDbEntrySet> outQueue = new LinkedBlockingDeque<>();

    private final int numThreads;

    private ExecutorService executor;

    private final List<Retriever> retrievers;

    private final DataSourceWrapper datasource;

    private final Map<String, EjbcaCaInfo> fpCaInfoMap;

    private final String selectCertSql;

    private final String selectRawCertSql;

    private final AtomicInteger numSkippedCerts = new AtomicInteger(0);

    public EjbcaDigestExportReader(final DataSourceWrapper datasource,
            final Map<String, EjbcaCaInfo> fpCaInfoMap, final int numThreads) throws Exception {
        this.datasource = ParamUtil.requireNonNull("datasource", datasource);
        this.numThreads = numThreads;
        this.fpCaInfoMap = ParamUtil.requireNonNull("fpCaInfoMap", fpCaInfoMap);

        selectCertSql = "SELECT id,fingerprint,serialNumber,cAFingerprint,status,revocationReason,"
                + "revocationDate FROM CertificateData WHERE id>=? AND id<? ORDER BY id ASC";

        selectRawCertSql = "SELECT base64Cert FROM CertificateData WHERE id=?";

        retrievers = new ArrayList<>(numThreads);

        for (int i = 0; i < numThreads; i++) {
            Retriever retriever = new Retriever();
            retrievers.add(retriever);
        }

        executor = Executors.newFixedThreadPool(numThreads);
        for (Runnable runnable : retrievers) {
            executor.execute(runnable);
        }
    } // constructor

    public List<IdentifiedDbDigestEntry> readCerts(final List<IdRange> idRanges)
    throws DataAccessException {
        ParamUtil.requireNonNull("idRanges", idRanges);

        int size = idRanges.size();
        for (IdRange range : idRanges) {
            inQueue.add(range);
        }

        List<DigestDbEntrySet> results = new ArrayList<>(size);
        int numCerts = 0;
        for (int i = 0; i < size; i++) {
            try {
                DigestDbEntrySet result = outQueue.take();
                numCerts += result.getEntries().size();
                results.add(result);
            } catch (InterruptedException ex) {
                throw new DataAccessException("InterruptedException " + ex.getMessage(), ex);
            }
        }

        Collections.sort(results);
        List<IdentifiedDbDigestEntry> ret = new ArrayList<>(numCerts);

        for (DigestDbEntrySet result : results) {
            if (result.getException() == null) {
                ret.addAll(result.getEntries());
                continue;
            }

            throw new DataAccessException(
                    String.format("could not read from ID %s: %s", result.getStartId(),
                            result.getException().getMessage()),
                    result.getException());
        }

        return ret;
    } // method readCerts

    public int getNumThreads() {
        return numThreads;
    }

    public int getNumSkippedCerts() {
        return numSkippedCerts.get();
    }

    public void stop() {
        stop.set(true);
        executor.shutdownNow();
    }

    protected void releaseResources(final Statement ps, final ResultSet rs) {
        DbToolBase.releaseResources(datasource, ps, rs);
    }
}
