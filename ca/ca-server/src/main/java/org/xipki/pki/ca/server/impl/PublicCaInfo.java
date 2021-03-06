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

package org.xipki.pki.ca.server.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.x509.extension.X509ExtensionUtil;
import org.xipki.commons.common.util.CollectionUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.X509Cert;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.api.OperationException;
import org.xipki.pki.ca.api.OperationException.ErrorCode;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class PublicCaInfo {

    private final X500Principal subject;

    private final X500Name x500Subject;

    private final String c14nSubject;

    private final byte[] subjectKeyIdentifier;

    private final GeneralNames subjectAltName;

    private final BigInteger serialNumber;

    private final X509Cert caCertificate;

    private X509Certificate crlSignerCertificate;

    private final List<String> caCertUris;

    private final List<String> ocspUris;

    private final List<String> crlUris;

    private final List<String> deltaCrlUris;

    PublicCaInfo(final X509Certificate caCertificate, final List<String> caCertUris,
            final List<String> ocspUris, final List<String> crlUris,
            final List<String> deltaCrlUris) throws OperationException {
        ParamUtil.requireNonNull("caCertificate", caCertificate);

        this.caCertificate = new X509Cert(caCertificate);
        this.serialNumber = caCertificate.getSerialNumber();
        this.subject = caCertificate.getSubjectX500Principal();
        this.x500Subject = X500Name.getInstance(subject.getEncoded());
        this.c14nSubject = X509Util.canonicalizName(x500Subject);
        try {
            this.subjectKeyIdentifier = X509Util.extractSki(caCertificate);
        } catch (CertificateEncodingException ex) {
            throw new OperationException(ErrorCode.INVALID_EXTENSION, ex);
        }
        this.caCertUris = CollectionUtil.unmodifiableList(caCertUris);
        this.ocspUris = CollectionUtil.unmodifiableList(ocspUris);
        this.crlUris = CollectionUtil.unmodifiableList(crlUris);
        this.deltaCrlUris = CollectionUtil.unmodifiableList(deltaCrlUris);

        byte[] encodedSubjectAltName = caCertificate.getExtensionValue(
                Extension.subjectAlternativeName.getId());
        if (encodedSubjectAltName == null) {
            subjectAltName = null;
        } else {
            try {
                subjectAltName = GeneralNames.getInstance(
                        X509ExtensionUtil.fromExtensionValue(encodedSubjectAltName));
            } catch (IOException ex) {
                throw new OperationException(ErrorCode.INVALID_EXTENSION,
                        "invalid SubjectAltName extension in CA certificate");
            }
        }
    } // constructor

    PublicCaInfo(final X500Name subject, final BigInteger serialNumber,
            final GeneralNames subjectAltName, final byte[] subjectKeyIdentifier,
            final List<String> caCertUris, final List<String> ocspUris, final List<String> crlUris,
            final List<String> deltaCrlUris) throws OperationException {
        this.x500Subject = ParamUtil.requireNonNull("subject", subject);
        this.serialNumber = ParamUtil.requireNonNull("serialNumber", serialNumber);

        this.caCertificate = null;
        this.c14nSubject = X509Util.canonicalizName(subject);
        try {
            this.subject = new X500Principal(subject.getEncoded());
        } catch (IOException ex) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                    "invalid SubjectAltName extension in CA certificate");
        }

        this.subjectKeyIdentifier = (subjectKeyIdentifier == null) ? null
                : Arrays.clone(subjectKeyIdentifier);

        this.subjectAltName = subjectAltName;
        this.caCertUris = CollectionUtil.unmodifiableList(caCertUris);
        this.ocspUris = CollectionUtil.unmodifiableList(ocspUris);
        this.crlUris = CollectionUtil.unmodifiableList(crlUris);
        this.deltaCrlUris = CollectionUtil.unmodifiableList(deltaCrlUris);
    } // constructor

    public List<String> getCaCertUris() {
        return caCertUris;
    }

    public List<String> getOcspUris() {
        return ocspUris;
    }

    public List<String> getCrlUris() {
        return crlUris;
    }

    public List<String> getDeltaCrlUris() {
        return deltaCrlUris;
    }

    public X509Certificate getCrlSignerCertificate() {
        return crlSignerCertificate;
    }

    public void setCrlSignerCertificate(final X509Certificate crlSignerCert) {
        this.crlSignerCertificate = caCertificate.getCert().equals(crlSignerCert)
                ? null : crlSignerCert;
    }

    public X500Principal getSubject() {
        return subject;
    }

    public X500Name getX500Subject() {
        return x500Subject;
    }

    public String getC14nSubject() {
        return c14nSubject;
    }

    public GeneralNames getSubjectAltName() {
        return subjectAltName;
    }

    public byte[] getSubjectKeyIdentifer() {
        if (caCertificate != null) {
            return caCertificate.getSubjectKeyIdentifier();
        } else {
            return (subjectKeyIdentifier == null) ? null : Arrays.clone(subjectKeyIdentifier);
        }
    }

    public BigInteger getSerialNumber() {
        return serialNumber;
    }

    public X509Cert getCaCertificate() {
        return caCertificate;
    }

}
