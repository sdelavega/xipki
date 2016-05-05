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

package org.xipki.pki.ca.server.mgmt.api.x509;

import java.io.Serializable;

import org.xipki.commons.common.InvalidConfException;
import org.xipki.commons.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class X509ChangeCrlSignerEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;

    private String signerType;

    private String signerConf;

    private String base64Cert;

    private String crlControl;

    public X509ChangeCrlSignerEntry(final String name) throws InvalidConfException {
        this.name = ParamUtil.requireNonBlank("name", name);
    }

    public String getName() {
        return name;
    }

    public String getSignerType() {
        return signerType;
    }

    public void setSignerType(final String signerType) {
        this.signerType = signerType;
    }

    public String getSignerConf() {
        return signerConf;
    }

    public void setSignerConf(final String signerConf) {
        this.signerConf = signerConf;
    }

    public String getBase64Cert() {
        return base64Cert;
    }

    public void setBase64Cert(final String base64Cert) {
        this.base64Cert = base64Cert;
    }

    public String getCrlControl() {
        return crlControl;
    }

    public void setCrlControl(final String crlControl) {
        this.crlControl = crlControl;
    }

}
