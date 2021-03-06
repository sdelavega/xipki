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

package org.xipki.pki.ca.dbtool.diffdb;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.datasource.DataSourceWrapper;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.commons.security.HashAlgoType;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.dbtool.DbToolBase;
import org.xipki.pki.ca.dbtool.IdRange;
import org.xipki.pki.ca.dbtool.StopMe;
import org.xipki.pki.ca.dbtool.diffdb.io.DbDigestEntry;
import org.xipki.pki.ca.dbtool.diffdb.io.DigestDbEntrySet;
import org.xipki.pki.ca.dbtool.diffdb.io.EjbcaCaCertExtractor;
import org.xipki.pki.ca.dbtool.diffdb.io.IdentifiedDbDigestEntry;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class EjbcaDbDigestReader extends DbDigestReader {

    private static final Logger LOG = LoggerFactory.getLogger(EjbcaDbDigestReader.class);

    private final int caId;

    private final String selectCertSql;

    private final String selectBase64CertSql;

    private final String caFingerprint;

    private EjbcaDbDigestReader(final DataSourceWrapper datasource, final X509Certificate caCert,
            final int totalAccount, final int minId, final int maxId, final int numThreads,
            final int caId, final int numCertsToPredicate, final StopMe stopMe) throws Exception {
        super(datasource, caCert, totalAccount, minId, maxId, numThreads, numCertsToPredicate,
                stopMe);

        this.caId = caId;
        this.caFingerprint = HashAlgoType.SHA1.hexHash(caCert.getEncoded()).toLowerCase();
        this.selectBase64CertSql = "SELECT base64Cert FROM CertificateData WHERE id=?";

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT id,serialNumber,cAFingerprint,fingerprint");
        sb.append(",status,revocationDate,revocationReason");
        sb.append(" FROM CertificateData WHERE id>=? AND id<?");
        this.selectCertSql = sb.toString();
        if (!init()) {
            throw new Exception("could not initialize the EjbcaDigestReader");
        }
    } // constructor

    private class EjbcaDbRetriever implements Retriever {

        private Connection conn;

        private PreparedStatement selectCertStmt;

        private PreparedStatement selectBase64CertStmt;

        EjbcaDbRetriever() throws DataAccessException {
            this.conn = datasource.getConnection();
            try {
                selectCertStmt = datasource.prepareStatement(conn, selectCertSql);
                selectBase64CertStmt = datasource.prepareStatement(conn, selectBase64CertSql);
            } catch (DataAccessException ex) {
                datasource.returnConnection(conn);
                throw ex;
            }
        }

        @Override
        public void run() {
            while (!stopMe.stopMe()) {
                try {
                    IdRange idRange = inQueue.take();
                    query(idRange);
                } catch (InterruptedException ex) {
                    LOG.error("InterruptedException: {}", ex.getMessage());
                }
            }

            DbToolBase.releaseResources(datasource, selectCertStmt, null);
            releaseResources(selectBase64CertStmt, null);
            datasource.returnConnection(conn);
            selectCertStmt = null;
            selectBase64CertStmt = null;
        }

        @SuppressWarnings("resource")
        private void query(final IdRange idRange) {
            DigestDbEntrySet result = new DigestDbEntrySet(idRange.getFrom());

            ResultSet rs = null;
            try {
                selectCertStmt.setInt(1, idRange.getFrom());
                selectCertStmt.setInt(2, idRange.getTo() + 1);

                rs = selectCertStmt.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String caHash = rs.getString("cAFingerprint");
                    String hash = rs.getString("fingerprint");

                    boolean ofThisCa = caFingerprint.equals(caHash);
                    if (!ofThisCa && caHash.equals(hash)) {
                        // special case
                        try {
                            selectBase64CertStmt.setInt(1, id);
                            ResultSet base64Rs = selectBase64CertStmt.executeQuery();
                            base64Rs.next();
                            String b64Cert = base64Rs.getString("base64Cert");
                            base64Rs.close();
                            X509Certificate jceCert = readBase64Cert(b64Cert);
                            if (jceCert.getIssuerX500Principal()
                                    .equals(caCert.getSubjectX500Principal())) {
                                ofThisCa = true;
                            }
                        } catch (SQLException ex) {
                            throw datasource.translate(selectBase64CertSql, ex);
                        }
                    }

                    if (!ofThisCa) {
                        continue;
                    }

                    String serialStr = rs.getString("serialNumber");
                    BigInteger serial = new BigInteger(serialStr);
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
                    result.addEntry(new IdentifiedDbDigestEntry(cert, id));
                }
            } catch (Exception ex) {
                if (ex instanceof SQLException) {
                    ex = datasource.translate(selectCertSql, (SQLException) ex);
                }
                result.setException(ex);
            } finally {
                releaseResources(null, rs);
            }

            outQueue.add(result);
        } // method query

    } // class EjbcaDbRetriever

    public int getCaId() {
        return caId;
    }

    @Override
    protected Retriever getRetriever() throws DataAccessException {
        return new EjbcaDbRetriever();
    }

    public static EjbcaDbDigestReader getInstance(final DataSourceWrapper datasource,
            final int caId, final boolean dbContainsOtherCa, final int numThreads,
            final int numCertsToPredicate, final StopMe stopMe) throws Exception {
        ParamUtil.requireNonNull("datasource", datasource);

        Connection conn = datasource.getConnection();

        Statement stmt = null;
        ResultSet rs = null;
        String sql = null;

        X509Certificate caCert;
        int totalAccount;
        int minId;
        int maxId;

        try {
            stmt = datasource.createStatement(conn);

            sql = "SELECT data FROM CAData WHERE cAId=" + caId;
            rs = stmt.executeQuery(sql);
            if (!rs.next()) {
                throw new IllegalArgumentException("no CA with id '" + caId + "' is available");
            }

            String caData = rs.getString("data");
            rs.close();

            caCert = EjbcaCaCertExtractor.extractCaCert(caData);
            // account
            if (dbContainsOtherCa) {
                // ignore it due to performance
                totalAccount = -1;
            } else {
                sql = "SELECT COUNT(*) FROM CertificateData";
                rs = stmt.executeQuery(sql);
                totalAccount = rs.next() ? rs.getInt(1) : 0;
                rs.close();
            }

            // maxId
            sql = "SELECT MAX(id) FROM CertificateData";
            rs = stmt.executeQuery(sql);
            maxId = rs.next() ? rs.getInt(1) : 0;
            rs.close();

            sql = "SELECT MIN(id) FROM CertificateData";
            rs = stmt.executeQuery(sql);
            minId = rs.next() ? rs.getInt(1) : 1;

        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            DbToolBase.releaseResources(datasource, stmt, rs);
            datasource.returnConnection(conn);
        }

        return new EjbcaDbDigestReader(datasource, caCert, totalAccount,
                minId, maxId, numThreads, caId,
                numCertsToPredicate, stopMe);
    } // method getInstance

    private static X509Certificate readBase64Cert(String b64Cert) throws DataAccessException {
        try {
            return X509Util.parseBase64EncodedCert(b64Cert);
        } catch (Exception ex) {
            throw new DataAccessException("IOException", ex);
        }
    }

}
