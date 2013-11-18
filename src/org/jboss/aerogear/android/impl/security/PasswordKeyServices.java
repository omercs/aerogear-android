/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.android.impl.security;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.jboss.aerogear.AeroGearCrypto;
import org.jboss.aerogear.android.security.CryptoConfig;
import org.jboss.aerogear.android.security.EncryptionService;
import org.jboss.aerogear.android.security.EncryptionServiceType;
import org.jboss.aerogear.crypto.CryptoBox;
import org.jboss.aerogear.crypto.keys.KeyPair;

/**
 * This class will build a CryptoBox including keys from a keystore protected
 * by a password.
 * 
 * If a keystore does not exist, one will be created and saved on the device.
 * 
 */
public class PasswordKeyServices extends AbstractEncryptionService implements EncryptionService {

    private static final String TAG = PasswordKeyServices.class.getSimpleName();
    
    private final CryptoBox crypto;
    
    public PasswordKeyServices(PasswordProtectedKeystoreCryptoConfig config, Context appContext) {
        super(appContext);
        this.crypto = getCrypto(appContext, config);
    }
    
    
    private CryptoBox getCrypto(Context appContext, PasswordProtectedKeystoreCryptoConfig config) {
        validate(config);
        
        String keyAlias = config.getAlias();
        if (keyAlias == null) {
            throw new IllegalArgumentException("Alias in CryptoConfig may not be null");
        }
        
        char[] password = config.password.toCharArray();
        
        KeyStore.ProtectionParameter passwordProtectionParameter = new KeyStore.PasswordProtection(password);
        
        try {
            KeyStore store = KeyStore.getInstance("BKS");
            store.load(getKeystoreStream(appContext, config.getKeyStoreFile()), password);
            if (store.containsAlias(keyAlias)) {
                KeyStore.SecretKeyEntry keyEntry = (KeyStore.SecretKeyEntry) store.getEntry(keyAlias, passwordProtectionParameter);
                SecretKey key = keyEntry.getSecretKey();
                return new CryptoBox(key.getEncoded());
            } else {
                return createKey(store, config, appContext);
            }
        } catch (KeyStoreException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (NoSuchAlgorithmException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (UnrecoverableEntryException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (CertificateException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }

    }

    private CryptoBox createKey(KeyStore store, PasswordProtectedKeystoreCryptoConfig config, Context context) {
        KeyPair pair = new KeyPair();
        PrivateKey privateKey = pair.getPrivateKey();
        PublicKey publicKey = pair.getPublicKey();
        MessageDigest hash;
        KeyAgreement keyAgree;
        
        final char[] password = config.password.toCharArray();
        final String keyAlias = config.getAlias();
        final String keyStoreFile = config.getKeyStoreFile();
        final KeyStore.ProtectionParameter passwordProtectionParameter = new KeyStore.PasswordProtection(password);
        
        try {
            hash = MessageDigest.getInstance("SHA-256", AeroGearCrypto.PROVIDER);
            keyAgree = KeyAgreement.getInstance("ECDH", AeroGearCrypto.PROVIDER);
            keyAgree.init(privateKey);
            keyAgree.doPhase(publicKey, true);
        

            byte[] keyBytes = hash.digest(keyAgree.generateSecret());

            KeyStore.SecretKeyEntry secretEntry = new KeyStore.SecretKeyEntry(new SecretKeySpec(keyBytes, "ECDH"));
            store.setEntry(keyAlias, secretEntry, passwordProtectionParameter);
            store.store(context.openFileOutput(keyStoreFile, Context.MODE_PRIVATE), password);
            return  new CryptoBox(keyBytes);
            
        } catch (NoSuchAlgorithmException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (KeyStoreException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (NoSuchProviderException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (InvalidKeyException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (CertificateException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    
        
        
        
        
    }

    private InputStream getKeystoreStream(Context context, String keystoreFile) {
        File keystore = new File(context.getFilesDir(), keystoreFile);
        if (!keystore.exists()) {
            return null;
        } else {
            try {
                return new FileInputStream(keystore);
            } catch (FileNotFoundException ex) {
                //This shouldn't happen because we do an explicit check earlier...
                Log.e(TAG, ex.getMessage());
                throw new RuntimeException(ex);
            }
        }
    }
    
     private void validate(PasswordProtectedKeystoreCryptoConfig config) {
        
        if (config.alias == null) {
            throw new IllegalArgumentException("The alias must not be null");
        }
        
        if (config.password == null) {
            throw new IllegalArgumentException("The password must not be null");
        }
        
        if (config.keyStoreFile == null) {
            throw new IllegalArgumentException("The keystoreFile must not be null");
        }
    }

    @Override
    protected CryptoBox getCryptoInstance() {
        return crypto;
    }
    
    public static class PasswordProtectedKeystoreCryptoConfig implements CryptoConfig {
        private String alias;
        private String password;
        private String keyStoreFile = "default.keystore";
        
        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getKeyStoreFile() {
            return keyStoreFile;
        }

        public void setKeyStoreFile(String keyStoreFile) {
            this.keyStoreFile = keyStoreFile;
        }

        @Override
        public EncryptionServiceType getType() {
            return EncryptionServiceTypes.PASSWORD_KEYSTORE;
        }
        
    }
    
}
