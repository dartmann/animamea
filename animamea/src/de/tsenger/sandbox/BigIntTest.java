/**
 * 
 */
package de.tsenger.sandbox;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;

import de.tsenger.animamea.tools.HexString;

/**
 * @author Tobias Senger (tobias@t-senger.de)
 * 
 */
public class BigIntTest {

	BigInteger g = null;
	BigInteger s = null;
	BigInteger H = null;
	BigInteger p = null;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		p = new BigInteger("B10B8F96A080E01DDE92DE5EAE5D54EC52C99FBCFB06A3C6"
				+ "9A6A9DCA52D23B616073E28675A23D189838EF1E2EE652C0"
				+ "13ECB4AEA906112324975C3CD49B83BFACCBDD7D90C4BD70"
				+ "98488E9C219A73724EFFD6FAE5644738FAA31A4FF55BCCC0"
				+ "A151AF5F0DC8B4BD45BF37DF365C1A65E68CFDA76D4DA708"
				+ "DF1FB2BC2E4A4371", 16);
		g = new BigInteger("A4D1CBD5C3FD34126765A442EFB99905F8104DD258AC507F"
				+ "D6406CFF14266D31266FEA1E5C41564B777E690F5504F213"
				+ "160217B4B01B886A5E91547F9E2749F4D7FBD7D3B9A92EE1"
				+ "909D0D2263F80A76A6A24C087A091F531DBF0A0169B6A28A"
				+ "D662A4D18E73AFA32D779D5918D08BC8858F4DCEF97C2A24"
				+ "855E6EEB22B3B2E5", 16);
		s = new BigInteger("FA5B7E3E49753A0DB9178B7B9BD898C8", 16);
		H = new BigInteger("5BABEBEF5B74E5BA94B5C063FDA15F1F"
				+ "1CDE94873EE0A5D3A2FCAB49F258D07F"
				+ "544F13CB66658C3AFEE9E727389BE3F6"
				+ "CBBBD32128A8C21DD6EEA3CF7091CDDF"
				+ "B08B8D007D40318DCCA4FFBF51208790"
				+ "FB4BD111E5A968ED6B6F08B26CA87C41"
				+ "0B3CE0C310CE104EABD16629AA48620C"
				+ "1279270CB0750C0D37C57FFFE302AE7F", 16);
	}

	@Test
	public void test() {

		BigInteger g_Strich = g.modPow(s, p).multiply(H).mod(p);
		System.out.println(g_Strich.toString(16));
	}

	@Test
	public void test2() {
		byte sw0 = (byte) 0x90;
		byte sw1 = (byte) 0x00;
		System.out.println("Ergebnis:" + Math.abs(sw0 * 256 + sw1));
		System.out.println("Ergebnis:" + (((sw0 & 0xff) << 8) | (sw1 & 0xff)));
		System.out.println("0x9000:" + 0x9000);

	}
	
	@Test
	public void test3() {
		byte[] b1 = new byte[] {0,(byte)0x7f, 0x65};		
		BigInteger bi1 = new BigInteger(b1);
		System.out.println(HexString.bufferToHex(bi1.toByteArray()));
	}

}
