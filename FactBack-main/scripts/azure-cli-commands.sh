#!/bin/bash

##############################################################################
# Commandes Azure CLI pour configuration manuelle des rôles FactBack
#
# Ce fichier contient toutes les commandes nécessaires pour configurer
# les rôles Azure AD manuellement, sans utiliser le script automatique.
##############################################################################

# ============================================================================
# ÉTAPE 1 : Connexion à Azure
# ============================================================================

# Se connecter à Azure
az login

# Vérifier le compte actif
az account show

# Changer de subscription si nécessaire
# az account set --subscription "VOTRE_SUBSCRIPTION_ID"


# ============================================================================
# ÉTAPE 2 : Créer les groupes de sécurité Azure AD
# ============================================================================

# Créer le groupe ROLE_ADMIN
az ad group create \
  --display-name "FactBack-Admins" \
  --mail-nickname "factback-admins" \
  --description "Administrateurs de l'application FactBack"

# Créer le groupe ROLE_INVOICE_ADMIN
az ad group create \
  --display-name "FactBack-Invoice-Admins" \
  --mail-nickname "factback-invoice-admins" \
  --description "Administrateurs des factures"

# Créer le groupe ROLE_TEMPLATES_ADMIN
az ad group create \
  --display-name "FactBack-Templates-Admins" \
  --mail-nickname "factback-templates-admins" \
  --description "Administrateurs des templates"

# Créer le groupe ROLE_USER
az ad group create \
  --display-name "FactBack-Users" \
  --mail-nickname "factback-users" \
  --description "Utilisateurs standard"

# Créer le groupe ROLE_READONLY
az ad group create \
  --display-name "FactBack-ReadOnly" \
  --mail-nickname "factback-readonly" \
  --description "Utilisateurs en lecture seule"


# ============================================================================
# ÉTAPE 3 : Récupérer les Object IDs des groupes
# ============================================================================

# Récupérer l'Object ID du groupe ROLE_ADMIN
ADMIN_GROUP_ID=$(az ad group list \
  --filter "displayName eq 'FactBack-Admins'" \
  --query "[0].id" -o tsv)
echo "ROLE_ADMIN: $ADMIN_GROUP_ID"

# Récupérer l'Object ID du groupe ROLE_INVOICE_ADMIN
INVOICE_ADMIN_GROUP_ID=$(az ad group list \
  --filter "displayName eq 'FactBack-Invoice-Admins'" \
  --query "[0].id" -o tsv)
echo "ROLE_INVOICE_ADMIN: $INVOICE_ADMIN_GROUP_ID"

# Récupérer l'Object ID du groupe ROLE_TEMPLATES_ADMIN
TEMPLATES_ADMIN_GROUP_ID=$(az ad group list \
  --filter "displayName eq 'FactBack-Templates-Admins'" \
  --query "[0].id" -o tsv)
echo "ROLE_TEMPLATES_ADMIN: $TEMPLATES_ADMIN_GROUP_ID"

# Récupérer l'Object ID du groupe ROLE_USER
USER_GROUP_ID=$(az ad group list \
  --filter "displayName eq 'FactBack-Users'" \
  --query "[0].id" -o tsv)
echo "ROLE_USER: $USER_GROUP_ID"

# Récupérer l'Object ID du groupe ROLE_READONLY
READONLY_GROUP_ID=$(az ad group list \
  --filter "displayName eq 'FactBack-ReadOnly'" \
  --query "[0].id" -o tsv)
echo "ROLE_READONLY: $READONLY_GROUP_ID"


# ============================================================================
# ÉTAPE 4 : Ajouter des utilisateurs aux groupes
# ============================================================================

# Ajouter un utilisateur au groupe ROLE_ADMIN
# Remplacez USER_EMAIL par l'email de l'utilisateur
USER_EMAIL="admin@example.com"
USER_ID=$(az ad user show --id "$USER_EMAIL" --query id -o tsv)
az ad group member add --group "$ADMIN_GROUP_ID" --member-id "$USER_ID"

# Ajouter un utilisateur au groupe ROLE_USER
USER_EMAIL="user@example.com"
USER_ID=$(az ad user show --id "$USER_EMAIL" --query id -o tsv)
az ad group member add --group "$USER_GROUP_ID" --member-id "$USER_ID"

# Ajouter un utilisateur au groupe ROLE_READONLY
USER_EMAIL="readonly@example.com"
USER_ID=$(az ad user show --id "$USER_EMAIL" --query id -o tsv)
az ad group member add --group "$READONLY_GROUP_ID" --member-id "$USER_ID"


# ============================================================================
# ÉTAPE 5 : Lister les membres d'un groupe
# ============================================================================

# Lister les membres du groupe ROLE_ADMIN
az ad group member list --group "$ADMIN_GROUP_ID" --query "[].{Name:displayName, Email:mail}" -o table

# Lister tous les groupes créés
az ad group list --filter "startswith(displayName, 'FactBack')" --query "[].{Name:displayName, ObjectId:id}" -o table


# ============================================================================
# ÉTAPE 6 : Configurer Azure App Service
# ============================================================================

# Variables à définir
APP_SERVICE_NAME="votre-app-service"
RESOURCE_GROUP="votre-resource-group"

# Vérifier que l'App Service existe
az webapp show --name "$APP_SERVICE_NAME" --resource-group "$RESOURCE_GROUP"

# Configurer les variables d'environnement (Application Settings)
az webapp config appsettings set \
  --name "$APP_SERVICE_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --settings \
    "azure.ad.group.mapping.ROLE_ADMIN=$ADMIN_GROUP_ID" \
    "azure.ad.group.mapping.ROLE_INVOICE_ADMIN=$INVOICE_ADMIN_GROUP_ID" \
    "azure.ad.group.mapping.ROLE_TEMPLATES_ADMIN=$TEMPLATES_ADMIN_GROUP_ID" \
    "azure.ad.group.mapping.ROLE_USER=$USER_GROUP_ID" \
    "azure.ad.group.mapping.ROLE_READONLY=$READONLY_GROUP_ID"

# Redémarrer l'App Service pour appliquer les changements
az webapp restart --name "$APP_SERVICE_NAME" --resource-group "$RESOURCE_GROUP"


# ============================================================================
# ÉTAPE 7 : Vérifier la configuration
# ============================================================================

# Lister les application settings
az webapp config appsettings list \
  --name "$APP_SERVICE_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --query "[?contains(name, 'azure.ad.group')].{Name:name, Value:value}" \
  -o table

# Voir les logs en temps réel
az webapp log tail --name "$APP_SERVICE_NAME" --resource-group "$RESOURCE_GROUP"

# Télécharger les logs
az webapp log download \
  --name "$APP_SERVICE_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --log-file logs.zip


# ============================================================================
# ÉTAPE 8 : Nettoyer / Supprimer (si nécessaire)
# ============================================================================

# Supprimer un groupe
# az ad group delete --group "FactBack-Admins"

# Retirer un utilisateur d'un groupe
# az ad group member remove --group "$ADMIN_GROUP_ID" --member-id "$USER_ID"

# Supprimer une application setting
# az webapp config appsettings delete \
#   --name "$APP_SERVICE_NAME" \
#   --resource-group "$RESOURCE_GROUP" \
#   --setting-names "azure.ad.group.mapping.ROLE_ADMIN"


# ============================================================================
# COMMANDES UTILES
# ============================================================================

# Rechercher un utilisateur par email
az ad user show --id "user@example.com"

# Lister tous les utilisateurs du tenant
az ad user list --query "[].{Name:displayName, Email:mail}" -o table

# Rechercher un groupe par nom
az ad group list --filter "displayName eq 'FactBack-Admins'" -o table

# Vérifier si un utilisateur est membre d'un groupe
az ad group member check --group "$ADMIN_GROUP_ID" --member-id "$USER_ID"

# Obtenir des informations sur l'App Registration
APP_ID="votre-app-registration-client-id"
az ad app show --id "$APP_ID"

# Lister les App Registrations
az ad app list --query "[?contains(displayName, 'FactBack')].{Name:displayName, AppId:appId}" -o table
