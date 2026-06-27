#!/bin/bash

# ECDSA Key Generation Script for SimpleCabinet JWT
# This script generates ECDSA key pairs for JWT token signing

echo "Generating ECDSA key pair for JWT..."
echo ""

# Create keys directory
mkdir -p keys

# Generate private key (ECDSA P-256)
openssl ecparam -name prime256v1 -genkey -noout -out keys/private_key.pem

# Generate public key from private key
openssl ec -in keys/private_key.pem -pubout -out keys/public_key.pem

# Convert private key to PKCS8 format and Base64 encode
PRIVATE_KEY_BASE64=$(openssl pkcs8 -topk8 -nocrypt -in keys/private_key.pem -outform DER | base64 -w 0)

# Convert public key to DER format and Base64 encode
PUBLIC_KEY_BASE64=$(openssl ec -in keys/private_key.pem -pubout -outform DER | base64 -w 0)

echo "✓ Keys generated successfully!"
echo ""
echo "Keys saved to:"
echo "  - keys/private_key.pem"
echo "  - keys/public_key.pem"
echo ""
echo "===================================================================================="
echo "Add the following to your application.yaml:"
echo "===================================================================================="
echo ""
echo "jwt:"
echo "  private-key: $PRIVATE_KEY_BASE64"
echo "  public-key: $PUBLIC_KEY_BASE64"
echo "  expiration: 86400000"
echo "  session-expiration: 604800000"
echo ""
echo "===================================================================================="
echo ""
echo "⚠️  IMPORTANT: Keep the private key secure and never commit it to version control!"
echo ""
