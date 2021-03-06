/**
 *  Copyright 2011, Tobias Senger
 *  
 *  This file is part of animamea.
 *
 *  Animamea is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Animamea is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License   
 *  along with animamea.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tsenger.animamea.iso7816;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import org.bouncycastle.asn1.ASN1InputStream;

import de.tsenger.animamea.crypto.AmCryptoException;
import de.tsenger.animamea.crypto.AmCryptoProvider;
import de.tsenger.animamea.tools.HexString;

/**
 * Verpackt ungeschützte CAPDU in SecureMessaging und entpackt SM-geschützte RAPDU.
 * 
 * @author Tobias Senger (tobias@t-senger.de)
 *
 */
public class SecureMessaging {

	private byte[] ks_enc = null;
	private byte[] ks_mac = null;
	private byte[] ssc = null;
	private AmCryptoProvider crypto = null;

	/**
	 * Konstruktor
	 * 
	 * @param acp
	 *            AmDESCrypto- oder AmAESCrypto-Instanz
	 * @param ksenc
	 *            Session Key für Verschlüsselung (K_enc)
	 * @param ksmac
	 *            Session Key für Prüfsummenberechnung (K_mac)
	 * @param initssc
	 *            Initialer Wert des Send Sequence Counters
	 */
	public SecureMessaging(AmCryptoProvider acp, byte[] ksenc, byte[] ksmac,
			byte[] initialSSC) {

		crypto = acp;

		ks_enc = ksenc.clone();
		ks_mac = ksmac.clone();

		ssc = initialSSC.clone();

	}

	/**
	 * Erzeugt aus einer Plain-Command-APDU ohne Secure Messaging eine Command-APDU
	 * mit Secure Messaging.
	 * 
	 * @param capdu plain Command-APDU
	 * @return CommandAPDU mit SM
	 * @throws SecureMessagingException 
	 */
	public CommandAPDU wrap(CommandAPDU capdu) throws SecureMessagingException {

		byte[] header = null;
		DO97 do97 = null;
		DO87 do87 = null;
		DO8E do8E = null;

		incrementAtIndex(ssc, ssc.length - 1);

		// Mask class byte and pad command header
		header = new byte[4];
		
		// Die ersten 4 Bytes der CAPDU sind der Header 
		System.arraycopy(capdu.getBytes(), 0, header, 0, 4);
		
		// Markiert das Secure Messaging im CLA-Byte
		header[0] = (byte) (header[0] | (byte) 0x0C); 
		
		int apduCase = getAPDUStructure(capdu);

		// build DO87
		if (apduCase == 3 || apduCase == 4 || apduCase == 6) {
			do87 = buildDO87(capdu.getData().clone());
		}

		// build DO97
		if (apduCase == 2 || apduCase == 4 || apduCase == 7) {
			do97 = buildDO97(capdu.getNe());
		}

		// build DO8E
		do8E = buildDO8E(header, do87, do97);

		// construct and return protected APDU
		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		try {

			if (do87 != null) 
				bOut.write(do87.getEncoded());
			
			if (do97 != null)
				bOut.write(do97.getEncoded());
			
			bOut.write(do8E.getEncoded());

		} catch (IOException e) {
			throw new SecureMessagingException(e);
		}
		return new CommandAPDU(header[0], header[1], header[2], header[3], bOut.toByteArray(), 65536);
	}

	/**
	 *  Erzeugt aus einer SM geschützten Response-APDU eine plain Response-APDU
	 *  ohne Secure Messaging.
	 * @param rapdu SM protected RAPDU
	 * @return plain RAPDU
	 * @throws SecureMessagingException
	 */
	public ResponseAPDU unwrap(ResponseAPDU rapdu) throws SecureMessagingException{

		DO87 do87 = null;
		DO99 do99 = null;
		DO8E do8E = null;

		incrementAtIndex(ssc, ssc.length - 1);

		int pointer = 0;
		byte[] rapduBytes = rapdu.getData();
		byte[] subArray = new byte[rapduBytes.length];

		while (pointer < rapduBytes.length) {
			System.arraycopy(rapduBytes, pointer, subArray, 0,
					rapduBytes.length - pointer);
			ASN1InputStream asn1sp = new ASN1InputStream(subArray);
			byte[] encodedBytes = null;
			try {
				encodedBytes = asn1sp.readObject().getEncoded();
				asn1sp.close();
			} catch (IOException e) {
				throw new SecureMessagingException(e);
			}

			ASN1InputStream asn1in = new ASN1InputStream(encodedBytes);
			try {
				switch (encodedBytes[0]) {
				case (byte) 0x87:
					do87 = new DO87();
					do87.fromByteArray(asn1in.readObject().getEncoded());
					break;
				case (byte) 0x99:
					do99 = new DO99();
					do99.fromByteArray(asn1in.readObject().getEncoded());
					break;
				case (byte) 0x8E:
					do8E = new DO8E();
					do8E.fromByteArray(asn1in.readObject().getEncoded());
				}
				asn1in.close();
			} catch (IOException e) {
				throw new SecureMessagingException(e);
			}

			pointer += encodedBytes.length;
		}

		if (do99 == null)
			throw new SecureMessagingException("Secure Messaging error: mandatory DO99 not found"); // DO99 is mandatory
															// and only absent
															// if SM error
															// occurs

		// Construct K (SSC||DO87||DO99)
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try {
			if (do87 != null) bout.write(do87.getEncoded());
			bout.write(do99.getEncoded());
		} catch (IOException e) {
			throw new SecureMessagingException(e);
		}

		crypto.init(ks_mac, ssc);
		byte[] cc = crypto.getMAC(bout.toByteArray());

		byte[] do8eData = do8E.getData();

		if (!java.util.Arrays.equals(cc, do8eData))
			throw new SecureMessagingException("Checksum is incorrect!\n Calculated CC: "
					+ HexString.bufferToHex(cc) + "\nCC in DO8E: "
					+ HexString.bufferToHex(do8eData));

		// Decrypt DO87
		byte[] data = null;
		byte[] unwrappedAPDUBytes = null;

		if (do87 != null) {
			crypto.init(ks_enc, ssc);
			byte[] do87Data = do87.getData();
			try {
				data = crypto.decrypt(do87Data);
			} catch (AmCryptoException e) {
				throw new SecureMessagingException(e);
			}
			// Build unwrapped RAPDU
			unwrappedAPDUBytes = new byte[data.length + 2];
			System.arraycopy(data, 0, unwrappedAPDUBytes, 0, data.length);
			byte[] do99Data = do99.getData();
			System.arraycopy(do99Data, 0, unwrappedAPDUBytes, data.length,
					do99Data.length);
		} else
			unwrappedAPDUBytes = do99.getData().clone();

		return new ResponseAPDU(unwrappedAPDUBytes);
	}

	
	/**
	 * encrypt data with KS.ENC and build DO87
	 * @param data
	 * @return
	 * @throws SecureMessagingException
	 */
	private DO87 buildDO87(byte[] data) throws SecureMessagingException  {

		crypto.init(ks_enc, ssc);
		byte[] enc_data;
		try {
			enc_data = crypto.encrypt(data);
		} catch (AmCryptoException e) {
			throw new SecureMessagingException(e);
		}

		return new DO87(enc_data);

	}

	private DO8E buildDO8E(byte[] header, DO87 do87, DO97 do97) throws SecureMessagingException {

		ByteArrayOutputStream m = new ByteArrayOutputStream();

		/**
		 * Verhindert doppeltes Padden des Headers: Nur wenn do87 oder do97
		 * vorhanden sind, wird der Header gepaddet. Ansonsten wird erst beim
		 * Berechnen des MAC gepaddet.
		 */
		try {
			if (do87 != null || do97 != null)
				m.write(crypto.addPadding(header));
			
			else
				m.write(header);

			if (do87 != null)
				m.write(do87.getEncoded());
			if (do97 != null)
				m.write(do97.getEncoded());
		} catch (IOException e) {
			throw new SecureMessagingException(e);
		}

		crypto.init(ks_mac, ssc);

		return new DO8E(crypto.getMAC(m.toByteArray()));
	}

	private DO97 buildDO97(int le) {
		return new DO97(le);
	}

	/**
	 * Bestimmt welchem Case die CAPDU enstpricht. (Siehe ISO/IEC 7816-3 Kapitel
	 * 12.1)
	 * 
	 * @return Strukurtype (1 = CASE1, ...)
	 */
	private byte getAPDUStructure(CommandAPDU capdu) {
		byte[] cardcmd = capdu.getBytes();

		if (cardcmd.length == 4)
			return 1;
		if (cardcmd.length == 5)
			return 2;
		if (cardcmd.length == (5 + (cardcmd[4]&0xff)) && cardcmd[4] != 0)
			return 3;
		if (cardcmd.length == (6 + (cardcmd[4]&0xff)) && cardcmd[4] != 0)
			return 4;
		if (cardcmd.length == 7 && cardcmd[4] == 0)
			return 5;
		if (cardcmd.length == (7 + (cardcmd[5]&0xff) * 256 + (cardcmd[6]&0xff))
				&& cardcmd[4] == 0 && (cardcmd[5] != 0 || cardcmd[6] != 0))
			return 6;
		if (cardcmd.length == (9 + (cardcmd[5]&0xff) * 256 + (cardcmd[6]&0xff))
				&& cardcmd[4] == 0 && (cardcmd[5] != 0 || cardcmd[6] != 0))
			return 7;
		return 0;
	}

	private void incrementAtIndex(byte[] array, int index) {
		if (array[index] == Byte.MAX_VALUE) {
			array[index] = 0;
			if (index > 0)
				incrementAtIndex(array, index - 1);
		} else {
			array[index]++;
		}
	}
}
