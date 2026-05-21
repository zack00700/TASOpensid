# Scripts de configuration Azure AD pour FactBack

Ce dossier contient des scripts pour automatiser la configuration des rôles Azure AD.

## 📋 Contenu

- **`setup-azure-ad-roles.sh`** - Script interactif automatique (recommandé)
- **`azure-cli-commands.sh`** - Commandes Azure CLI pour configuration manuelle
- **`README.md`** - Ce fichier

---

## 🚀 Option 1 : Script automatique (recommandé)

### Prérequis

1. **Azure CLI installé**
   ```bash
   # Vérifier l'installation
   az --version

   # Installer si nécessaire
   # macOS: brew install azure-cli
   # Windows: https://aka.ms/installazurecliwindows
   # Linux: curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash
   ```

2. **Connexion à Azure**
   ```bash
   az login
   ```

3. **Permissions requises**
   - Créer des groupes de sécurité dans Azure AD
   - Configurer les App Service (si vous voulez configurer automatiquement)

### Utilisation

#### Mode interactif (le plus simple)

```bash
cd scripts
./setup-azure-ad-roles.sh
```

Le script va vous guider à travers :
1. ✅ Création des groupes de sécurité Azure AD
2. ✅ Récupération des Object IDs
3. ✅ Ajout d'utilisateurs aux groupes (optionnel)
4. ✅ Configuration de l'App Service (optionnel)
5. ✅ Sauvegarde des Object IDs dans `azure-group-ids.txt`

#### Créer uniquement les groupes

```bash
./setup-azure-ad-roles.sh --create-groups
```

#### Créer les groupes avec un préfixe personnalisé

```bash
./setup-azure-ad-roles.sh --prefix "MonEntreprise" --create-groups
```

Cela créera :
- `MonEntreprise-Admins`
- `MonEntreprise-Users`
- etc.

#### Créer les groupes et configurer l'App Service

```bash
./setup-azure-ad-roles.sh \
  --create-groups \
  --configure-app \
  --app-name "factback-prod" \
  --resource-group "factback-rg"
```

#### Voir toutes les options

```bash
./setup-azure-ad-roles.sh --help
```

### Résultat

Le script génère un fichier **`azure-group-ids.txt`** contenant les mappings :

```properties
azure.ad.group.mapping.ROLE_ADMIN=12345678-1234-1234-1234-123456789abc
azure.ad.group.mapping.ROLE_INVOICE_ADMIN=23456789-2345-2345-2345-234567890bcd
azure.ad.group.mapping.ROLE_USER=45678901-4567-4567-4567-456789012def
azure.ad.group.mapping.ROLE_READONLY=56789012-5678-5678-5678-567890123ef0
```

---

## 🛠️ Option 2 : Configuration manuelle

Si vous préférez exécuter les commandes manuellement :

### Étape 1 : Consulter le fichier de commandes

```bash
cat azure-cli-commands.sh
```

### Étape 2 : Exécuter les commandes une par une

```bash
# Se connecter
az login

# Créer un groupe
az ad group create \
  --display-name "FactBack-Admins" \
  --mail-nickname "factback-admins" \
  --description "Administrateurs de l'application FactBack"

# Récupérer l'Object ID
az ad group list \
  --filter "displayName eq 'FactBack-Admins'" \
  --query "[0].id" -o tsv
```

### Étape 3 : Ajouter des utilisateurs

```bash
# Remplacez par l'email de l'utilisateur
USER_EMAIL="admin@example.com"
GROUP_NAME="FactBack-Admins"

# Récupérer les IDs
USER_ID=$(az ad user show --id "$USER_EMAIL" --query id -o tsv)
GROUP_ID=$(az ad group list --filter "displayName eq '$GROUP_NAME'" --query "[0].id" -o tsv)

# Ajouter l'utilisateur au groupe
az ad group member add --group "$GROUP_ID" --member-id "$USER_ID"
```

### Étape 4 : Configurer l'App Service

```bash
APP_SERVICE_NAME="votre-app-service"
RESOURCE_GROUP="votre-resource-group"

# Récupérer les Object IDs des groupes
ADMIN_GROUP_ID=$(az ad group list --filter "displayName eq 'FactBack-Admins'" --query "[0].id" -o tsv)
USER_GROUP_ID=$(az ad group list --filter "displayName eq 'FactBack-Users'" --query "[0].id" -o tsv)
# ... (répéter pour tous les groupes)

# Configurer les application settings
az webapp config appsettings set \
  --name "$APP_SERVICE_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --settings \
    "azure.ad.group.mapping.ROLE_ADMIN=$ADMIN_GROUP_ID" \
    "azure.ad.group.mapping.ROLE_USER=$USER_GROUP_ID"

# Redémarrer
az webapp restart --name "$APP_SERVICE_NAME" --resource-group "$RESOURCE_GROUP"
```

---

## 📝 Après l'exécution du script

### 1. Configurer le claim "groups" dans l'App Registration

⚠️ **Cette étape ne peut pas être automatisée et doit être faite manuellement.**

1. Allez sur **Azure Portal** → **Azure Active Directory** → **App registrations**
2. Sélectionnez votre application FactBack
3. **Token configuration** → **+ Add groups claim**
4. Sélectionnez **Security groups**
5. Cochez **Group ID** (important pour éviter la limite de 5 groupes)
6. Cliquez **Add**

### 2. Redémarrer l'App Service

Si vous avez configuré l'App Service avec le script :

```bash
az webapp restart --name <APP_SERVICE_NAME> --resource-group <RESOURCE_GROUP>
```

Ou dans le portail Azure : **App Service** → **Overview** → **Restart**

### 3. Vérifier les logs

```bash
az webapp log tail --name <APP_SERVICE_NAME> --resource-group <RESOURCE_GROUP>
```

Vous devriez voir :
```
✅ Azure AD group mappings configured: 5 groups mapped
✅ Mapped 2 groups to 2 roles
✅ Security context updated with mapped roles for user: john.doe@example.com
```

---

## 🔍 Vérification

### Lister les groupes créés

```bash
az ad group list \
  --filter "startswith(displayName, 'FactBack')" \
  --query "[].{Name:displayName, ObjectId:id}" \
  -o table
```

### Lister les membres d'un groupe

```bash
GROUP_ID="votre-group-object-id"
az ad group member list \
  --group "$GROUP_ID" \
  --query "[].{Name:displayName, Email:mail}" \
  -o table
```

### Vérifier la configuration de l'App Service

```bash
az webapp config appsettings list \
  --name "<APP_SERVICE_NAME>" \
  --resource-group "<RESOURCE_GROUP>" \
  --query "[?contains(name, 'azure.ad.group')].{Name:name, Value:value}" \
  -o table
```

---

## 🐛 Dépannage

### Problème : "az: command not found"

**Solution** : Installez Azure CLI
```bash
# macOS
brew install azure-cli

# Windows (PowerShell en admin)
Invoke-WebRequest -Uri https://aka.ms/installazurecliwindows -OutFile .\AzureCLI.msi
Start-Process msiexec.exe -Wait -ArgumentList '/I AzureCLI.msi /quiet'

# Linux
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash
```

### Problème : "Please run 'az login' to setup account"

**Solution** : Connectez-vous à Azure
```bash
az login
```

### Problème : "Insufficient privileges to complete the operation"

**Solution** : Vous n'avez pas les permissions nécessaires
- Contactez votre administrateur Azure AD
- Vous devez avoir le rôle "Groups Administrator" ou "Global Administrator"

### Problème : Le groupe existe déjà

Le script détecte automatiquement les groupes existants et les réutilise.

### Problème : "No groups claim found in JWT token"

**Solution** : Configurez le claim "groups" dans l'App Registration (voir étape 1 ci-dessus)

---

## 📚 Ressources

- [Documentation Azure CLI](https://docs.microsoft.com/cli/azure/)
- [Azure AD Groups](https://docs.microsoft.com/azure/active-directory/fundamentals/active-directory-groups-create-azure-portal)
- [Configuration des tokens](https://docs.microsoft.com/azure/active-directory/develop/active-directory-optional-claims)
- [Guide complet FactBack](../AZURE_AD_ROLES_SETUP.md)

---

## 🔐 Sécurité

⚠️ **Important** :
- Ne partagez pas le fichier `azure-group-ids.txt` publiquement
- Ne committez pas ce fichier dans Git (il est dans `.gitignore`)
- Utilisez des variables d'environnement dans Azure pour les Object IDs
- Revoyez périodiquement les membres des groupes

---

## 💡 Exemples d'utilisation

### Scénario 1 : Première installation

```bash
# Créer les groupes et configurer tout automatiquement
./setup-azure-ad-roles.sh
```

### Scénario 2 : Environnement de test

```bash
# Créer des groupes avec un préfixe "FactBack-Test"
./setup-azure-ad-roles.sh --prefix "FactBack-Test" --create-groups
```

### Scénario 3 : Plusieurs App Services

```bash
# Créer les groupes une fois
./setup-azure-ad-roles.sh --create-groups

# Configurer plusieurs App Services avec les mêmes groupes
./setup-azure-ad-roles.sh --configure-app --app-name "factback-dev" --resource-group "dev-rg"
./setup-azure-ad-roles.sh --configure-app --app-name "factback-prod" --resource-group "prod-rg"
```

### Scénario 4 : Ajouter un nouvel utilisateur

```bash
# Utiliser Azure CLI directement
USER_EMAIL="nouveau@example.com"
GROUP_NAME="FactBack-Users"

USER_ID=$(az ad user show --id "$USER_EMAIL" --query id -o tsv)
GROUP_ID=$(az ad group list --filter "displayName eq '$GROUP_NAME'" --query "[0].id" -o tsv)
az ad group member add --group "$GROUP_ID" --member-id "$USER_ID"
```

---

## 📞 Support

Pour des questions ou problèmes :
1. Consultez le guide complet : `../AZURE_AD_ROLES_SETUP.md`
2. Vérifiez les logs de l'App Service
3. Testez avec les commandes manuelles dans `azure-cli-commands.sh`
