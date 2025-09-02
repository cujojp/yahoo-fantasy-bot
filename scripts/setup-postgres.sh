#!/bin/bash

# PostgreSQL Setup and Management Script for Yahoo Fantasy Bot
# This script helps you install, start, and configure PostgreSQL

echo "=== PostgreSQL Setup for Yahoo Fantasy Bot ==="
echo ""

# Detect OS
OS="$(uname -s)"
ARCH="$(uname -m)"

# Function to check if PostgreSQL is installed
check_postgres_installed() {
    if command -v psql &> /dev/null && command -v pg_isready &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# Function to check if PostgreSQL is running
check_postgres_running() {
    pg_isready &> /dev/null
    return $?
}

# Function to start PostgreSQL
start_postgres() {
    echo "Starting PostgreSQL..."
    
    case "$OS" in
        Darwin)  # macOS
            if command -v brew &> /dev/null; then
                # Check if PostgreSQL was installed via Homebrew
                if brew list postgresql@14 &>/dev/null || brew list postgresql@15 &>/dev/null || brew list postgresql@16 &>/dev/null; then
                    echo "Starting PostgreSQL via Homebrew..."
                    brew services start postgresql
                elif brew list postgresql &>/dev/null; then
                    echo "Starting PostgreSQL via Homebrew..."
                    brew services start postgresql
                else
                    echo "PostgreSQL not found via Homebrew"
                    return 1
                fi
            else
                echo "Homebrew not found. Trying manual start..."
                # Try common PostgreSQL data directories
                if [ -d "/usr/local/var/postgres" ]; then
                    pg_ctl -D /usr/local/var/postgres start
                elif [ -d "/opt/homebrew/var/postgres" ]; then
                    pg_ctl -D /opt/homebrew/var/postgres start
                elif [ -d "$HOME/Library/Application Support/Postgres/var-14" ]; then
                    # Postgres.app default location
                    echo "Detected Postgres.app - please start it from Applications"
                    return 1
                else
                    echo "Could not find PostgreSQL data directory"
                    return 1
                fi
            fi
            ;;
        Linux)
            # Try systemctl first (systemd)
            if command -v systemctl &> /dev/null; then
                echo "Starting PostgreSQL via systemctl..."
                sudo systemctl start postgresql
            # Try service command (SysV init)
            elif command -v service &> /dev/null; then
                echo "Starting PostgreSQL via service..."
                sudo service postgresql start
            else
                echo "Could not determine how to start PostgreSQL on this Linux system"
                return 1
            fi
            ;;
        *)
            echo "Unsupported operating system: $OS"
            return 1
            ;;
    esac
}

# Function to install PostgreSQL
install_postgres() {
    echo "PostgreSQL is not installed. Would you like to install it?"
    echo ""
    
    case "$OS" in
        Darwin)  # macOS
            echo "Installation options for macOS:"
            echo "1. Homebrew (recommended): brew install postgresql"
            echo "2. Postgres.app: Download from https://postgresapp.com"
            echo "3. Official installer: https://www.postgresql.org/download/macosx/"
            echo ""
            
            if command -v brew &> /dev/null; then
                read -p "Install PostgreSQL via Homebrew? (y/n) " -n 1 -r
                echo ""
                if [[ $REPLY =~ ^[Yy]$ ]]; then
                    echo "Installing PostgreSQL..."
                    brew install postgresql
                    brew services start postgresql
                    echo "✅ PostgreSQL installed and started"
                fi
            else
                echo "Homebrew not found. Please install Homebrew first:"
                echo "/bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
            fi
            ;;
        Linux)
            echo "Installation commands for Linux:"
            echo ""
            echo "Ubuntu/Debian:"
            echo "  sudo apt update"
            echo "  sudo apt install postgresql postgresql-contrib"
            echo ""
            echo "RHEL/CentOS/Fedora:"
            echo "  sudo dnf install postgresql postgresql-server"
            echo "  sudo postgresql-setup --initdb"
            echo ""
            echo "Arch Linux:"
            echo "  sudo pacman -S postgresql"
            echo "  sudo -u postgres initdb -D /var/lib/postgres/data"
            ;;
    esac
}

# Main execution
echo "1. Checking PostgreSQL installation..."
if check_postgres_installed; then
    echo "✅ PostgreSQL is installed"
    PSQL_VERSION=$(psql --version | head -n1)
    echo "   Version: $PSQL_VERSION"
else
    echo "❌ PostgreSQL is not installed"
    install_postgres
    exit 1
fi

echo ""
echo "2. Checking if PostgreSQL is running..."
if check_postgres_running; then
    echo "✅ PostgreSQL is running"
    pg_isready
else
    echo "❌ PostgreSQL is not running"
    start_postgres
    
    # Wait a moment and check again
    sleep 2
    if check_postgres_running; then
        echo "✅ PostgreSQL started successfully"
    else
        echo "❌ Failed to start PostgreSQL"
        echo ""
        echo "Troubleshooting tips:"
        echo "1. Check PostgreSQL logs:"
        case "$OS" in
            Darwin)
                echo "   - Homebrew: brew services info postgresql"
                echo "   - Logs: /opt/homebrew/var/log/postgresql@*.log or /usr/local/var/log/postgresql@*.log"
                ;;
            Linux)
                echo "   - journalctl -xe | grep postgres"
                echo "   - /var/log/postgresql/*.log"
                ;;
        esac
        echo "2. Check if port 5432 is already in use:"
        echo "   lsof -i :5432"
        exit 1
    fi
fi

echo ""
echo "3. Checking database and user setup..."

# Try to connect as current user first
if psql -d postgres -c "SELECT 1" &> /dev/null; then
    echo "✅ Can connect to PostgreSQL as current user"
    POSTGRES_USER=$USER
else
    echo "⚠️  Cannot connect as current user, trying postgres user..."
    POSTGRES_USER="postgres"
fi

# Check if yahoo_fantasy_bot database exists
echo ""
echo "4. Checking for yahoo_fantasy_bot database..."
DB_EXISTS=$(psql -U $POSTGRES_USER -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='yahoo_fantasy_bot'" 2>/dev/null)

if [ "$DB_EXISTS" = "1" ]; then
    echo "✅ Database 'yahoo_fantasy_bot' exists"
    
    # Try to connect to it
    if psql -U $POSTGRES_USER -d yahoo_fantasy_bot -c "SELECT 1" &> /dev/null; then
        echo "✅ Can connect to yahoo_fantasy_bot database"
    else
        echo "❌ Cannot connect to yahoo_fantasy_bot database"
    fi
else
    echo "❌ Database 'yahoo_fantasy_bot' does not exist"
    read -p "Create it now? (y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if [ "$POSTGRES_USER" = "postgres" ]; then
            sudo -u postgres createdb yahoo_fantasy_bot
        else
            createdb yahoo_fantasy_bot
        fi
        
        if [ $? -eq 0 ]; then
            echo "✅ Database created successfully"
        else
            echo "❌ Failed to create database"
            echo "Try manually: createdb yahoo_fantasy_bot"
        fi
    fi
fi

echo ""
echo "5. Connection string for your .env file:"
echo "-------------------------------------------"
if [ "$POSTGRES_USER" = "$USER" ]; then
    echo "JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/yahoo_fantasy_bot"
else
    echo "JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/yahoo_fantasy_bot?user=postgres&password=postgres"
    echo ""
    echo "Note: You may need to set a password for the postgres user:"
    echo "sudo -u postgres psql -c \"ALTER USER postgres PASSWORD 'postgres';\""
fi

echo ""
echo "=== PostgreSQL Management Commands ==="
echo ""
echo "Start PostgreSQL:"
case "$OS" in
    Darwin)
        if command -v brew &> /dev/null; then
            echo "  brew services start postgresql"
        fi
        ;;
    Linux)
        echo "  sudo systemctl start postgresql"
        ;;
esac

echo ""
echo "Stop PostgreSQL:"
case "$OS" in
    Darwin)
        if command -v brew &> /dev/null; then
            echo "  brew services stop postgresql"
        fi
        ;;
    Linux)
        echo "  sudo systemctl stop postgresql"
        ;;
esac

echo ""
echo "Check status:"
echo "  pg_isready"

echo ""
echo "Connect to database:"
echo "  psql -d yahoo_fantasy_bot"

echo ""
echo "=== Next Steps ==="
echo "1. Update your .env file with the connection string above"
echo "2. Run: ./scripts/test-yahoo-connection.sh"
echo "3. Start the backend: ./gradlew :backend:run"
echo "4. Authenticate with Yahoo at http://localhost:8080"
echo "5. Start the bot: ./gradlew :bot:run"
