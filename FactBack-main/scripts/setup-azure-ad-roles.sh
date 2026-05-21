#!/bin/bash

##############################################################################
# Script de configuration des rôles Azure AD pour FactBack
#
# Ce script automatise :
# 1. La création des groupes de sécurité Azure AD
# 2. La récupération des Object IDs
# 3. L'ajout d'utilisateurs aux groupes
# 4. La configuration des variables d'environnement dans Azure App Service
#
# Prérequis :
# - Azure CLI installé (az)
# - Connecté à Azure (az login)
# - Permissions pour créer des groupes et configurer l'App Service
##############################################################################

set -e

# Couleurs pour l'affichage
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Variables par défaut
APP_SERVICE_NAME=""
RESOURCE_GROUP=""
PREFIX="FactBack"

# Définition des rôles
declare -A ROLES=(
    ["ROLE_ADMIN"]="Administrateurs de l'application FactBack"
    ["ROLE_INVOICE_ADMIN"]="Administrateurs des factures"
    ["ROLE_TEMPLATES_ADMIN"]="Administrateurs des templates"
    ["ROLE_USER"]="Utilisateurs standard"
    ["ROLE_READONLY"]="Utilisateurs en lecture seule"
)

# Mapping des noms de groupes
declare -A GROUP_NAMES=(
    ["ROLE_ADMIN"]="$PREFIX-Admins"
    ["ROLE_INVOICE_ADMIN"]="$PREFIX-Invoice-Admins"
    ["ROLE_TEMPLATES_ADMIN"]="$PREFIX-Templates-Admins"
    ["ROLE_USER"]="$PREFIX-Users"
    ["ROLE_READONLY"]="$PREFIX-ReadOnly"
)

##############################################################################
# Fonctions utilitaires
##############################################################################

print_header() {
    echo -e "\n${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

check_azure_cli() {
    if ! command -v az &> /dev/null; then
        print_error "Azure CLI n'est pas installé"
        echo "Installez-le depuis : https://docs.microsoft.com/cli/azure/install-azure-cli"
        exit 1
    fi
    print_success "Azure CLI détecté"
}

check_azure_login() {
    if ! az account show &> /dev/null; then
        print_error "Non connecté à Azure"
        echo "Exécutez : az login"
        exit 1
    fi

    local account=$(az account show --query name -o tsv 2>/dev/null)
    print_success "Connecté à Azure : $account"
}

##############################################################################
# Fonction principale : Création des groupes
##############################################################################

create_groups() {
    print_header "Création des groupes de sécurité Azure AD"

    declare -A GROUP_IDS

    for role in "${!ROLES[@]}"; do
        local group_name="${GROUP_NAMES[$role]}"
        local description="${ROLES[$role]}"

        echo -e "\n${BLUE}Création du groupe :${NC} $group_name"

        # Vérifier si le groupe existe déjà
        local existing_group=$(az ad group list --filter "displayName eq '$group_name'" --query "[0].id" -o tsv 2>/dev/null)

        if [ -n "$existing_group" ]; then
            print_warning "Le groupe existe déjà : $group_name"
            GROUP_IDS[$role]=$existing_group
            print_info "Object ID : $existing_group"
        else
            # Créer le groupe
            local group_id=$(az ad group create \
                --display-name "$group_name" \
                --mail-nickname "$(echo $group_name | tr ' ' '-' | tr '[:upper:]' '[:lower:]')" \
                --description "$description" \
                --query id -o tsv)

            if [ $? -eq 0 ]; then
                print_success "Groupe créé : $group_name"
                GROUP_IDS[$role]=$group_id
                print_info "Object ID : $group_id"
            else
                print_error "Échec de la création du groupe : $group_name"
            fi
        fi
    done

    # Sauvegarder les IDs dans un fichier
    echo -e "\n${BLUE}Sauvegarde des Object IDs...${NC}"
    {
        echo "# Azure AD Group Object IDs pour FactBack"
        echo "# Généré le $(date)"
        echo ""
        for role in "${!GROUP_IDS[@]}"; do
            echo "azure.ad.group.mapping.$role=${GROUP_IDS[$role]}"
        done
    } > azure-group-ids.txt

    print_success "Object IDs sauvegardés dans : azure-group-ids.txt"

    # Retourner les IDs
    for role in "${!GROUP_IDS[@]}"; do
        echo "$role=${GROUP_IDS[$role]}"
    done
}

##############################################################################
# Fonction : Ajouter des utilisateurs aux groupes
##############################################################################

add_users_to_groups() {
    print_header "Ajout d'utilisateurs aux groupes"

    echo "Voulez-vous ajouter des utilisateurs aux groupes maintenant ? (o/n)"
    read -r response

    if [[ ! "$response" =~ ^[oO]$ ]]; then
        print_info "Ignoré. Vous pouvez ajouter des utilisateurs plus tard via le portail Azure."
        return
    fi

    for role in "${!GROUP_NAMES[@]}"; do
        local group_name="${GROUP_NAMES[$role]}"

        echo -e "\n${BLUE}Groupe :${NC} $group_name ($role)"
        echo "Entrez les adresses email des utilisateurs à ajouter (séparées par des espaces, ou 'skip' pour passer) :"
        read -r users

        if [[ "$users" == "skip" ]] || [[ -z "$users" ]]; then
            continue
        fi

        # Récupérer l'Object ID du groupe
        local group_id=$(az ad group list --filter "displayName eq '$group_name'" --query "[0].id" -o tsv)

        for email in $users; do
            # Récupérer l'Object ID de l'utilisateur
            local user_id=$(az ad user show --id "$email" --query id -o tsv 2>/dev/null)

            if [ -n "$user_id" ]; then
                # Ajouter l'utilisateur au groupe
                az ad group member add --group "$group_id" --member-id "$user_id" 2>/dev/null

                if [ $? -eq 0 ]; then
                    print_success "Ajouté : $email"
                else
                    print_warning "Déjà membre ou erreur : $email"
                fi
            else
                print_error "Utilisateur introuvable : $email"
            fi
        done
    done
}

##############################################################################
# Fonction : Configurer l'App Registration Azure AD
##############################################################################

configure_app_registration() {
    print_header "Configuration de l'App Registration Azure AD"

    echo "Entrez l'Application (client) ID de votre App Registration :"
    read -r app_id

    if [ -z "$app_id" ]; then
        print_warning "Application ID non fourni. Configuration manuelle requise."
        echo ""
        echo "Pour configurer manuellement :"
        echo "1. Azure Portal → Azure Active Directory → App registrations"
        echo "2. Sélectionnez votre application"
        echo "3. Token configuration → Add groups claim"
        echo "4. Sélectionnez 'Security groups' et 'Group ID'"
        return
    fi

    print_info "Configuration du claim 'groups' pour l'application : $app_id"

    # Note : La configuration du claim 'groups' via Azure CLI n'est pas directement supportée
    # Il faut utiliser Microsoft Graph API ou le portail

    print_warning "La configuration du claim 'groups' doit être faite manuellement :"
    echo ""
    echo "1. Allez sur : https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationMenuBlade/~/TokenConfiguration/appId/$app_id"
    echo "2. Cliquez sur '+ Add groups claim'"
    echo "3. Sélectionnez 'Security groups'"
    echo "4. Cochez 'Group ID' dans les options"
    echo "5. Cliquez sur 'Add'"
}

##############################################################################
# Fonction : Configurer Azure App Service
##############################################################################

configure_app_service() {
    print_header "Configuration d'Azure App Service"

    if [ -z "$APP_SERVICE_NAME" ] || [ -z "$RESOURCE_GROUP" ]; then
        echo "Entrez le nom de votre App Service :"
        read -r APP_SERVICE_NAME

        echo "Entrez le nom du Resource Group :"
        read -r RESOURCE_GROUP
    fi

    if [ -z "$APP_SERVICE_NAME" ] || [ -z "$RESOURCE_GROUP" ]; then
        print_warning "App Service ou Resource Group non fourni. Configuration ignorée."
        return
    fi

    print_info "Configuration de l'App Service : $APP_SERVICE_NAME"

    # Vérifier que l'App Service existe
    if ! az webapp show --name "$APP_SERVICE_NAME" --resource-group "$RESOURCE_GROUP" &> /dev/null; then
        print_error "App Service introuvable : $APP_SERVICE_NAME"
        return
    fi

    # Lire les Object IDs depuis le fichier
    if [ ! -f "azure-group-ids.txt" ]; then
        print_error "Fichier azure-group-ids.txt introuvable"
        return
    fi

    echo -e "\n${BLUE}Configuration des variables d'environnement...${NC}"

    # Extraire et configurer chaque mapping
    while IFS='=' read -r key value; do
        # Ignorer les commentaires et lignes vides
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue

        # Configurer l'app setting
        az webapp config appsettings set \
            --name "$APP_SERVICE_NAME" \
            --resource-group "$RESOURCE_GROUP" \
            --settings "$key=$value" \
            --output none

        if [ $? -eq 0 ]; then
            print_success "Configuré : $key"
        else
            print_error "Échec : $key"
        fi
    done < azure-group-ids.txt

    print_success "Configuration de l'App Service terminée"

    echo -e "\n${YELLOW}Redémarrez l'App Service pour appliquer les changements :${NC}"
    echo "az webapp restart --name $APP_SERVICE_NAME --resource-group $RESOURCE_GROUP"
}

##############################################################################
# Fonction : Afficher le résumé
##############################################################################

show_summary() {
    print_header "Résumé de la configuration"

    if [ -f "azure-group-ids.txt" ]; then
        echo -e "${BLUE}Groupes créés et leurs Object IDs :${NC}\n"
        cat azure-group-ids.txt | grep -v "^#" | grep -v "^$"

        echo -e "\n${GREEN}Configuration sauvegardée dans :${NC} azure-group-ids.txt"
    fi

    echo -e "\n${YELLOW}Prochaines étapes :${NC}"
    echo "1. Configurez le claim 'groups' dans votre App Registration Azure AD"
    echo "2. Redémarrez votre App Service si configuré"
    echo "3. Testez la connexion avec un utilisateur membre d'un groupe"
    echo "4. Vérifiez les logs : 'Azure AD group mappings configured: X groups mapped'"

    if [ -n "$APP_SERVICE_NAME" ]; then
        echo -e "\n${BLUE}Commande pour redémarrer l'App Service :${NC}"
        echo "az webapp restart --name $APP_SERVICE_NAME --resource-group $RESOURCE_GROUP"
    fi

    echo -e "\n${BLUE}Commande pour voir les logs :${NC}"
    echo "az webapp log tail --name <APP_SERVICE_NAME> --resource-group <RESOURCE_GROUP>"
}

##############################################################################
# Fonction : Mode interactif
##############################################################################

interactive_mode() {
    print_header "Configuration des rôles Azure AD pour FactBack"

    echo "Ce script va vous aider à configurer les rôles Azure AD."
    echo ""

    # Vérifications préalables
    check_azure_cli
    check_azure_login

    # Étape 1 : Créer les groupes
    create_groups

    # Étape 2 : Ajouter des utilisateurs
    add_users_to_groups

    # Étape 3 : Configurer l'App Registration
    configure_app_registration

    # Étape 4 : Configurer l'App Service
    echo -e "\nVoulez-vous configurer Azure App Service maintenant ? (o/n)"
    read -r response
    if [[ "$response" =~ ^[oO]$ ]]; then
        configure_app_service
    fi

    # Résumé
    show_summary
}

##############################################################################
# Fonction : Afficher l'aide
##############################################################################

show_help() {
    cat << EOF
Usage: $0 [OPTIONS]

Configure les rôles Azure AD pour FactBack

Options:
    -h, --help              Afficher cette aide
    -i, --interactive       Mode interactif (défaut)
    -c, --create-groups     Créer uniquement les groupes
    -a, --add-users         Ajouter des utilisateurs aux groupes
    -s, --configure-app     Configurer l'App Service
        --app-name NAME     Nom de l'App Service
        --resource-group RG Nom du Resource Group
        --prefix PREFIX     Préfixe pour les noms de groupes (défaut: FactBack)

Exemples:
    # Mode interactif
    $0

    # Créer uniquement les groupes
    $0 --create-groups

    # Créer les groupes et configurer l'App Service
    $0 --create-groups --configure-app --app-name myapp --resource-group myRG

    # Utiliser un préfixe personnalisé
    $0 --prefix "MyCompany" --create-groups

Prérequis:
    - Azure CLI installé : https://docs.microsoft.com/cli/azure/install-azure-cli
    - Connecté à Azure : az login
    - Permissions Azure AD pour créer des groupes
    - Permissions pour configurer l'App Service

EOF
}

##############################################################################
# Point d'entrée principal
##############################################################################

main() {
    local mode="interactive"
    local create_groups_only=false
    local add_users_only=false
    local configure_app_only=false

    # Parser les arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -i|--interactive)
                mode="interactive"
                shift
                ;;
            -c|--create-groups)
                create_groups_only=true
                mode="manual"
                shift
                ;;
            -a|--add-users)
                add_users_only=true
                mode="manual"
                shift
                ;;
            -s|--configure-app)
                configure_app_only=true
                mode="manual"
                shift
                ;;
            --app-name)
                APP_SERVICE_NAME="$2"
                shift 2
                ;;
            --resource-group)
                RESOURCE_GROUP="$2"
                shift 2
                ;;
            --prefix)
                PREFIX="$2"
                # Mettre à jour les noms de groupes
                GROUP_NAMES["ROLE_ADMIN"]="$PREFIX-Admins"
                GROUP_NAMES["ROLE_INVOICE_ADMIN"]="$PREFIX-Invoice-Admins"
                GROUP_NAMES["ROLE_TEMPLATES_ADMIN"]="$PREFIX-Templates-Admins"
                GROUP_NAMES["ROLE_USER"]="$PREFIX-Users"
                GROUP_NAMES["ROLE_READONLY"]="$PREFIX-ReadOnly"
                shift 2
                ;;
            *)
                print_error "Option inconnue : $1"
                show_help
                exit 1
                ;;
        esac
    done

    # Exécuter selon le mode
    if [ "$mode" = "interactive" ]; then
        interactive_mode
    else
        check_azure_cli
        check_azure_login

        if [ "$create_groups_only" = true ]; then
            create_groups
        fi

        if [ "$add_users_only" = true ]; then
            add_users_to_groups
        fi

        if [ "$configure_app_only" = true ]; then
            configure_app_service
        fi

        show_summary
    fi

    print_success "Terminé !"
}

# Exécuter le script
main "$@"
