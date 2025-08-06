import { useState } from 'react';
import { Link } from 'react-router-dom';
import { 
  Database, 
  Plus, 
  Settings, 
  BarChart3, 
  MessageSquare, 
  Users, 
  Activity,
  Zap,
  CheckCircle,
  AlertCircle,
  Clock,
  ArrowRight,
  Trash2,
  Edit,
  Eye,
  Brain
} from 'lucide-react';

interface DatabaseConnection {
  id: string;
  name: string;
  type: 'mysql' | 'postgresql' | 'mongodb';
  host: string;
  port: number;
  database: string;
  status: 'connected' | 'disconnected' | 'error';
  lastSync: string;
  tables: number;
  size: string;
}

const DashboardPage = () => {
  const [showAddConnection, setShowAddConnection] = useState(false);
  const [selectedDatabase, setSelectedDatabase] = useState<string | null>(null);

  // Mock data for database connections
  const [connections, setConnections] = useState<DatabaseConnection[]>([
    {
      id: '1',
      name: 'Production MySQL',
      type: 'mysql',
      host: 'mysql.production.com',
      port: 3306,
      database: 'ecommerce_db',
      status: 'connected',
      lastSync: '2 minutes ago',
      tables: 24,
      size: '2.4 GB'
    },
    {
      id: '2',
      name: 'Analytics PostgreSQL',
      type: 'postgresql',
      host: 'postgres.analytics.com',
      port: 5432,
      database: 'analytics_db',
      status: 'connected',
      lastSync: '5 minutes ago',
      tables: 18,
      size: '1.8 GB'
    },
    {
      id: '3',
      name: 'User Data MongoDB',
      type: 'mongodb',
      host: 'mongo.users.com',
      port: 27017,
      database: 'users_db',
      status: 'error',
      lastSync: '1 hour ago',
      tables: 12,
      size: '856 MB'
    }
  ]);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'connected':
        return 'var(--semantic-success)';
      case 'error':
        return 'var(--semantic-error)';
      case 'disconnected':
        return 'var(--semantic-warning)';
      default:
        return 'var(--neutral-gray)';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'connected':
        return <CheckCircle size={16} />;
      case 'error':
        return <AlertCircle size={16} />;
      case 'disconnected':
        return <Clock size={16} />;
      default:
        return <Clock size={16} />;
    }
  };

  const getDatabaseIcon = (type: string) => {
    switch (type) {
      case 'mysql':
        return '🐬';
      case 'postgresql':
        return '🐘';
      case 'mongodb':
        return '🍃';
      default:
        return '🗄️';
    }
  };

  const handleDeleteConnection = (id: string) => {
    setConnections(connections.filter(conn => conn.id !== id));
  };

  return (
    <div className="min-h-screen" style={{ background: '#f8fafc' }}>
      {/* Navigation */}
      <nav className="nav">
        <div className="nav-container">
          <Link to="/" className="nav-logo">
            <Database size={32} />
            dbVybe
          </Link>
          <ul className="nav-menu">
            <li><Link to="/" className="nav-menu-item">Home</Link></li>
            <li><Link to="/dashboard" className="nav-menu-item" style={{ color: 'var(--primary-purple)' }}>Dashboard</Link></li>
            <li><Link to="/explore" className="nav-menu-item">Explore</Link></li>
            <li><Link to="/login" className="nav-menu-item">Login</Link></li>
          </ul>
        </div>
      </nav>

      <div className="container" style={{ paddingTop: '32px', paddingBottom: '32px' }}>
        {/* Header */}
        <div style={{ marginBottom: '32px' }}>
          <h1 style={{ 
            fontSize: 'var(--font-size-3xl)', 
            fontWeight: 'var(--font-weight-bold)',
            marginBottom: '8px'
          }}>
            Dashboard
          </h1>
          <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-lg)' }}>
            Manage your database connections and monitor their status
          </p>
        </div>

        {/* Stats Cards */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))',
          gap: '24px',
          marginBottom: '32px'
        }}>
          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-primary)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <Database size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Total Connections
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  {connections.length}
                </p>
              </div>
            </div>
          </div>

          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-blue)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <CheckCircle size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Connected
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  {connections.filter(c => c.status === 'connected').length}
                </p>
              </div>
            </div>
          </div>

          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-pink)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <BarChart3 size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Total Tables
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  {connections.reduce((sum, conn) => sum + conn.tables, 0)}
                </p>
              </div>
            </div>
          </div>

          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-vibrant)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <Zap size={24} color="white" />
              </div>
              <div>
                <p style={{ color: 'var(--neutral-gray)', fontSize: 'var(--font-size-sm)' }}>
                  Total Size
                </p>
                <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}>
                  5.1 GB
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Database Connections */}
        <div className="card" style={{ marginBottom: '32px' }}>
          <div style={{ 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            marginBottom: '24px'
          }}>
            <h2 style={{ 
              fontSize: 'var(--font-size-xl)', 
              fontWeight: 'var(--font-weight-bold)'
            }}>
              Database Connections
            </h2>
            <button 
              className="btn btn-primary"
              onClick={() => setShowAddConnection(true)}
            >
              <Plus size={20} />
              Add Connection
            </button>
          </div>

          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))',
            gap: '20px'
          }}>
            {connections.map((connection) => (
              <div 
                key={connection.id}
                className="card translate-hover"
                style={{ 
                  border: selectedDatabase === connection.id ? '2px solid var(--primary-purple)' : '1px solid var(--neutral-light-gray)',
                  cursor: 'pointer'
                }}
                onClick={() => setSelectedDatabase(connection.id)}
              >
                <div style={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'flex-start',
                  marginBottom: '16px'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={{ fontSize: '24px' }}>{getDatabaseIcon(connection.type)}</span>
                    <div>
                      <h3 style={{ 
                        fontSize: 'var(--font-size-lg)', 
                        fontWeight: 'var(--font-weight-semibold)',
                        marginBottom: '4px'
                      }}>
                        {connection.name}
                      </h3>
                      <p style={{ 
                        fontSize: 'var(--font-size-sm)', 
                        color: 'var(--neutral-gray)',
                        textTransform: 'capitalize'
                      }}>
                        {connection.type}
                      </p>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button 
                      className="btn btn-ghost"
                      style={{ padding: '4px' }}
                      onClick={(e) => {
                        e.stopPropagation();
                        // Handle view
                      }}
                    >
                      <Eye size={16} />
                    </button>
                    <button 
                      className="btn btn-ghost"
                      style={{ padding: '4px' }}
                      onClick={(e) => {
                        e.stopPropagation();
                        // Handle edit
                      }}
                    >
                      <Edit size={16} />
                    </button>
                    <button 
                      className="btn btn-ghost"
                      style={{ padding: '4px', color: 'var(--semantic-error)' }}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeleteConnection(connection.id);
                      }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>

                <div style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '8px',
                  marginBottom: '12px'
                }}>
                  {getStatusIcon(connection.status)}
                  <span style={{ 
                    fontSize: 'var(--font-size-sm)',
                    color: getStatusColor(connection.status)
                  }}>
                    {connection.status}
                  </span>
                </div>

                <div style={{ 
                  display: 'grid', 
                  gridTemplateColumns: '1fr 1fr',
                  gap: '12px',
                  fontSize: 'var(--font-size-sm)'
                }}>
                  <div>
                    <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Host</p>
                    <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                      {connection.host}:{connection.port}
                    </p>
                  </div>
                  <div>
                    <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Database</p>
                    <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                      {connection.database}
                    </p>
                  </div>
                  <div>
                    <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Tables</p>
                    <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                      {connection.tables}
                    </p>
                  </div>
                  <div>
                    <p style={{ color: 'var(--neutral-gray)', marginBottom: '4px' }}>Size</p>
                    <p style={{ fontWeight: 'var(--font-weight-medium)' }}>
                      {connection.size}
                    </p>
                  </div>
                </div>

                <div style={{ 
                  marginTop: '16px',
                  paddingTop: '16px',
                  borderTop: '1px solid var(--neutral-light-gray)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}>
                  <span style={{ 
                    fontSize: 'var(--font-size-sm)', 
                    color: 'var(--neutral-gray)'
                  }}>
                    Last sync: {connection.lastSync}
                  </span>
                  <Link 
                    to={`/explore?db=${connection.id}`}
                    className="btn btn-ghost"
                    style={{ 
                      fontSize: 'var(--font-size-sm)',
                      padding: '4px 8px'
                    }}
                  >
                    Explore
                    <ArrowRight size={16} />
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="card">
          <h2 style={{ 
            fontSize: 'var(--font-size-xl)', 
            fontWeight: 'var(--font-weight-bold)',
            marginBottom: '24px'
          }}>
            Quick Actions
          </h2>
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '16px'
          }}>
            <Link to="/explore" className="card translate-hover" style={{ 
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <Brain size={32} style={{ 
                color: 'var(--primary-purple)',
                marginBottom: '12px'
              }} />
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Ask Questions
              </h3>
              <p style={{ 
                fontSize: 'var(--font-size-sm)', 
                color: 'var(--neutral-gray)'
              }}>
                Use natural language to query your databases
              </p>
            </Link>

            <Link to="/explore" className="card translate-hover" style={{ 
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <BarChart3 size={32} style={{ 
                color: 'var(--secondary-blue)',
                marginBottom: '12px'
              }} />
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Create Reports
              </h3>
              <p style={{ 
                fontSize: 'var(--font-size-sm)', 
                color: 'var(--neutral-gray)'
              }}>
                Generate visualizations and insights
              </p>
            </Link>

            <Link to="/explore" className="card translate-hover" style={{ 
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <MessageSquare size={32} style={{ 
                color: 'var(--secondary-pink)',
                marginBottom: '12px'
              }} />
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Share Insights
              </h3>
              <p style={{ 
                fontSize: 'var(--font-size-sm)', 
                color: 'var(--neutral-gray)'
              }}>
                Collaborate with your team
              </p>
            </Link>

            <Link to="/explore" className="card translate-hover" style={{ 
              textDecoration: 'none',
              color: 'inherit',
              textAlign: 'center',
              padding: '24px'
            }}>
              <Activity size={32} style={{ 
                color: 'var(--semantic-info)',
                marginBottom: '12px'
              }} />
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '8px'
              }}>
                Monitor Performance
              </h3>
              <p style={{ 
                fontSize: 'var(--font-size-sm)', 
                color: 'var(--neutral-gray)'
              }}>
                Track query performance and usage
              </p>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage; 