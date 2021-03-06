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

package org.xipki.pki.ca.server.mgmt.api;

import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.xipki.commons.common.ConfPairs;
import org.xipki.commons.common.InvalidConfException;
import org.xipki.commons.common.util.CollectionUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.security.util.AlgorithmUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CmpControl {

    public static final String ALGO_DELIMITER = ":";

    public static final String KEY_CONFIRM_CERT = "confirm.cert";

    public static final String KEY_SEND_CA = "send.ca";

    public static final String KEY_SEND_RESPONDER = "send.responder";

    public static final String KEY_MESSAGETIME_REQUIRED = "messageTime.required";

    public static final String KEY_MESSAGETIME_BIAS = "messageTime.bias";

    public static final String KEY_CONFIRM_WAITTIME = "confirm.waittime";

    public static final String KEY_PROTECTION_SIGALGO = "protection.sigalgo";

    private static final int DFLT_MESSAGE_TIME_BIAS = 300; // 300 seconds

    private static final int DFLT_CONFIRM_WAIT_TIME = 300; // 300 seconds

    private final CmpControlEntry dbEntry;

    private final boolean confirmCert;

    private final boolean sendCaCert;

    private final boolean messageTimeRequired;

    private final boolean sendResponderCert;

    private final int messageTimeBias;

    private final int confirmWaitTime;

    private final Set<String> sigAlgos;

    public CmpControl(final CmpControlEntry dbEntry) throws InvalidConfException {
        ParamUtil.requireNonNull("dbEntry", dbEntry);

        ConfPairs pairs = new ConfPairs(dbEntry.getConf());
        this.confirmCert = getBoolean(pairs, KEY_CONFIRM_CERT, false);
        this.sendCaCert = getBoolean(pairs, KEY_SEND_CA, false);
        this.sendResponderCert = getBoolean(pairs, KEY_SEND_RESPONDER, true);
        this.messageTimeRequired = getBoolean(pairs, KEY_MESSAGETIME_REQUIRED, true);
        this.messageTimeBias = getInt(pairs, KEY_MESSAGETIME_BIAS, DFLT_MESSAGE_TIME_BIAS);
        this.confirmWaitTime = getInt(pairs, KEY_CONFIRM_WAITTIME, DFLT_CONFIRM_WAIT_TIME);
        String str = pairs.getValue(KEY_PROTECTION_SIGALGO);

        if (str == null) {
            this.sigAlgos = null;
        } else {
            Set<String> set = StringUtil.splitAsSet(str, ALGO_DELIMITER);
            this.sigAlgos = canonicalizeAlgos(set);
            if (CollectionUtil.isNonEmpty(this.sigAlgos)) {
                pairs.putPair(KEY_PROTECTION_SIGALGO,
                        StringUtil.collectionAsString(this.sigAlgos, ALGO_DELIMITER));
            }
        }

        this.dbEntry = new CmpControlEntry(dbEntry.getName(), pairs.getEncoded());
    } // constructor

    public CmpControl(final String name, final Boolean confirmCert, final Boolean sendCaCert,
            final Boolean messageTimeRequired, final Boolean sendResponderCert,
            final Integer messageTimeBias, final Integer confirmWaitTime,
            final Set<String> sigAlgos) throws InvalidConfException {
        ParamUtil.requireNonBlank("name", name);

        ConfPairs pairs = new ConfPairs();

        this.confirmCert = (confirmCert == null) ? false : confirmCert;
        pairs.putPair(KEY_CONFIRM_CERT, Boolean.toString(this.confirmCert));

        this.sendCaCert = (sendCaCert == null) ? false : sendCaCert;
        pairs.putPair(KEY_SEND_CA, Boolean.toString(this.sendCaCert));

        this.messageTimeRequired = (messageTimeRequired == null) ? true : messageTimeRequired;
        pairs.putPair(KEY_MESSAGETIME_REQUIRED, Boolean.toString(this.messageTimeRequired));

        this.sendResponderCert = (sendResponderCert == null) ? true
                : sendResponderCert.booleanValue();
        pairs.putPair(KEY_SEND_RESPONDER, Boolean.toString(this.sendResponderCert));

        this.messageTimeBias = (messageTimeBias == null) ? DFLT_MESSAGE_TIME_BIAS : messageTimeBias;
        pairs.putPair(KEY_MESSAGETIME_BIAS, Integer.toString(this.messageTimeBias));

        this.confirmWaitTime = (confirmWaitTime == null) ? DFLT_CONFIRM_WAIT_TIME : confirmWaitTime;
        pairs.putPair(KEY_CONFIRM_WAITTIME, Integer.toString(this.confirmWaitTime));

        if (CollectionUtil.isEmpty(sigAlgos)) {
            this.sigAlgos = null;
        } else {
            this.sigAlgos = canonicalizeAlgos(sigAlgos);
            pairs.putPair(KEY_PROTECTION_SIGALGO,
                    StringUtil.collectionAsString(this.sigAlgos, ALGO_DELIMITER));
        }

        this.dbEntry = new CmpControlEntry(name, pairs.getEncoded());
    } // constructor

    public boolean isMessageTimeRequired() {
        return messageTimeRequired;
    }

    public boolean isConfirmCert() {
        return confirmCert;
    }

    public int getMessageTimeBias() {
        return messageTimeBias;
    }

    public int getConfirmWaitTime() {
        return confirmWaitTime;
    }

    public boolean isSendCaCert() {
        return sendCaCert;
    }

    public boolean isSendResponderCert() {
        return sendResponderCert;
    }

    public Set<String> getSigAlgos() {
        return sigAlgos;
    }

    public boolean isSigAlgoPermitted(final AlgorithmIdentifier algId) {
        ParamUtil.requireNonNull("algId", algId);

        if (sigAlgos == null) {
            return true;
        }

        String name;
        try {
            name = AlgorithmUtil.getSignatureAlgoName(algId);
        } catch (NoSuchAlgorithmException ex) {
            return false;
        }

        return sigAlgos.contains(name);
    }

    public CmpControlEntry getDbEntry() {
        return dbEntry;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(500);
        sb.append("name: ").append(dbEntry.getName()).append('\n');
        sb.append("confirmCert: ").append(getYesNo(confirmCert)).append('\n');
        sb.append("sendCaCert: ").append(getYesNo(sendCaCert)).append("\n");
        sb.append("sendResponderCert: ").append(getYesNo(sendResponderCert)).append("\n");
        sb.append("messageTimeRequired: ").append(getYesNo(messageTimeRequired)).append("\n");
        sb.append("messageTimeBias: ").append(messageTimeBias).append(" s").append('\n');
        sb.append("confirmWaitTime: ").append(confirmWaitTime).append(" s").append('\n');
        sb.append("signature algos: ")
            .append(StringUtil.collectionAsString(sigAlgos, ALGO_DELIMITER)).append('\n');
        sb.append("conf: ").append(dbEntry.getConf());

        return sb.toString();
    }

    private static Set<String> canonicalizeAlgos(final Set<String> algos)
    throws InvalidConfException {
        Set<String> ret = new HashSet<String>();
        for (String m : algos) {
            try {
                ret.add(AlgorithmUtil.canonicalizeSignatureAlgo(m));
            } catch (NoSuchAlgorithmException ex) {
                throw new InvalidConfException(ex.getMessage(), ex);
            }
        }
        return Collections.unmodifiableSet(ret);
    }

    private static boolean getBoolean(final ConfPairs pairs, final String key,
            final boolean defaultValue) {
        String str = pairs.getValue(key);
        boolean ret = StringUtil.isBlank(str) ? defaultValue : Boolean.parseBoolean(str);
        pairs.putPair(key, Boolean.toString(ret));
        return ret;
    }

    private static int getInt(final ConfPairs pairs, final String key, final int defaultValue) {
        String str = pairs.getValue(key);
        int ret = StringUtil.isBlank(str) ? defaultValue : Integer.parseInt(str);
        pairs.putPair(key, Integer.toString(ret));
        return ret;
    }

    private static String getYesNo(boolean bo) {
        return bo ? "yes" : "no";
    }

}
