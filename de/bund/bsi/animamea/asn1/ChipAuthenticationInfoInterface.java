/**
 * 
 */
package de.bund.bsi.animamea.asn1;

/**
 * @author Tobias Senger (tobias.senger@bsi.bund.de)
 *
 */
public interface ChipAuthenticationInfoInterface {
	
	public String getProtocolOID();
	public int getVersion();
	public int getKeyId();
	
}
