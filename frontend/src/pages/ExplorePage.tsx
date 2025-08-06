import { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { 
  Database, 
  Send, 
  Brain, 
  BarChart3, 
  Download, 
  Share2, 
  Loader2,
  MessageSquare,
  Table,
  PieChart
} from 'lucide-react';

interface Message {
  id: string;
  type: 'user' | 'ai';
  content: string;
  timestamp: Date;
  data?: any;
  visualization?: any;
}

interface DatabaseInfo {
  id: string;
  name: string;
  type: string;
  tables: string[];
}

const ExplorePage = () => {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      type: 'ai',
      content: "Hello! I'm Vybie. I can help you explore and analyze your databases. What would you like to know about your database?",
      timestamp: new Date()
    }
  ]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [selectedDatabase, setSelectedDatabase] = useState<string>('1');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Mock database data
  const databases: DatabaseInfo[] = [
    {
      id: '1',
      name: 'Production MySQL',
      type: 'mysql',
      tables: ['users', 'orders', 'products', 'categories', 'reviews', 'payments']
    },
    {
      id: '2',
      name: 'Analytics PostgreSQL',
      type: 'postgresql',
      tables: ['events', 'sessions', 'page_views', 'conversions', 'metrics']
    },
    {
      id: '3',
      name: 'User Data MongoDB',
      type: 'mongodb',
      tables: ['profiles', 'preferences', 'activity_logs', 'notifications']
    }
  ];

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSendMessage = async () => {
    if (!inputValue.trim() || isLoading) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      type: 'user',
      content: inputValue,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsLoading(true);

    // Simulate AI response
    setTimeout(() => {
      const aiResponse = generateAIResponse(inputValue);
      const aiMessage: Message = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: aiResponse.content,
        timestamp: new Date(),
        data: aiResponse.data,
        visualization: aiResponse.visualization
      };

      setMessages(prev => [...prev, aiMessage]);
      setIsLoading(false);
    }, 2000);
  };

  const generateAIResponse = (userInput: string) => {
    const input = userInput.toLowerCase();
    
    if (input.includes('sales') || input.includes('revenue')) {
      return {
        content: "I found sales data in your database! Here's a summary of your revenue metrics:",
        data: {
          totalRevenue: 1250000,
          monthlyGrowth: 12.5,
          topProducts: ['Product A', 'Product B', 'Product C'],
          revenueByMonth: [
            { month: 'Jan', revenue: 95000 },
            { month: 'Feb', revenue: 102000 },
            { month: 'Mar', revenue: 118000 },
            { month: 'Apr', revenue: 125000 }
          ]
        },
        visualization: 'bar-chart'
      };
    } else if (input.includes('user') || input.includes('customer')) {
      return {
        content: "Here's what I found about your users:",
        data: {
          totalUsers: 15420,
          activeUsers: 8920,
          newUsersThisMonth: 1250,
          userGrowth: 8.3,
          topUserSegments: ['Premium', 'Standard', 'Basic']
        },
        visualization: 'pie-chart'
      };
    } else if (input.includes('product') || input.includes('inventory')) {
      return {
        content: "Here's your product inventory analysis:",
        data: {
          totalProducts: 1247,
          lowStock: 23,
          outOfStock: 5,
          topSelling: ['Product X', 'Product Y', 'Product Z'],
          categoryDistribution: [
            { category: 'Electronics', count: 456 },
            { category: 'Clothing', count: 234 },
            { category: 'Home', count: 189 },
            { category: 'Sports', count: 168 }
          ]
        },
        visualization: 'table'
      };
    } else {
      return {
        content: "I understand you're asking about your data. I can help you explore sales, users, products, and more. Try asking specific questions like 'Show me sales data' or 'How many users do we have?'",
        data: null,
        visualization: null
      };
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const renderVisualization = (data: any, type: string) => {
    if (!data) return null;

    switch (type) {
      case 'bar-chart':
        return (
          <div className="card" style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
              <BarChart3 size={20} color="var(--primary-purple)" />
              <h4 style={{ fontWeight: 'var(--font-weight-semibold)' }}>Revenue Analysis</h4>
            </div>
            <div style={{ display: 'flex', alignItems: 'end', gap: '8px', height: '120px' }}>
              {data.revenueByMonth?.map((item: any, index: number) => (
                <div key={index} style={{ flex: 1, textAlign: 'center' }}>
                  <div style={{
                    height: `${(item.revenue / 125000) * 100}px`,
                    background: 'var(--gradient-primary)',
                    borderRadius: '4px 4px 0 0',
                    marginBottom: '8px'
                  }} />
                  <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--neutral-gray)' }}>
                    {item.month}
                  </span>
                </div>
              ))}
            </div>
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
              gap: '16px',
              marginTop: '16px'
            }}>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>Total Revenue</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)' }}>
                  ${data.totalRevenue?.toLocaleString()}
                </p>
              </div>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>Growth</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)', color: 'var(--semantic-success)' }}>
                  +{data.monthlyGrowth}%
                </p>
              </div>
            </div>
          </div>
        );

      case 'pie-chart':
        return (
          <div className="card" style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
              <PieChart size={20} color="var(--secondary-blue)" />
              <h4 style={{ fontWeight: 'var(--font-weight-semibold)' }}>User Analytics</h4>
            </div>
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
              gap: '16px'
            }}>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>Total Users</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)' }}>
                  {data.totalUsers?.toLocaleString()}
                </p>
              </div>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>Active Users</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)' }}>
                  {data.activeUsers?.toLocaleString()}
                </p>
              </div>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>New This Month</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)', color: 'var(--semantic-success)' }}>
                  +{data.newUsersThisMonth?.toLocaleString()}
                </p>
              </div>
            </div>
          </div>
        );

      case 'table':
        return (
          <div className="card" style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
              <Table size={20} color="var(--secondary-pink)" />
              <h4 style={{ fontWeight: 'var(--font-weight-semibold)' }}>Product Inventory</h4>
            </div>
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
              gap: '16px'
            }}>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>Total Products</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)' }}>
                  {data.totalProducts?.toLocaleString()}
                </p>
              </div>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>Low Stock</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)', color: 'var(--semantic-warning)' }}>
                  {data.lowStock}
                </p>
              </div>
              <div>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--neutral-gray)' }}>Out of Stock</p>
                <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)', color: 'var(--semantic-error)' }}>
                  {data.outOfStock}
                </p>
              </div>
            </div>
          </div>
        );

      default:
        return null;
    }
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
            <li><Link to="/dashboard" className="nav-menu-item">Dashboard</Link></li>
            <li><Link to="/explore" className="nav-menu-item" style={{ color: 'var(--primary-purple)' }}>Explore</Link></li>
            <li><Link to="/login" className="nav-menu-item">Login</Link></li>
          </ul>
        </div>
      </nav>

      <div className="container" style={{ paddingTop: '32px', paddingBottom: '32px' }}>
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '1fr 300px',
          gap: '32px',
          height: 'calc(100vh - 200px)'
        }}>
          {/* Chat Interface */}
          <div className="card" style={{ 
            display: 'flex', 
            flexDirection: 'column',
            height: '100%'
          }}>
            <div style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: '12px',
              paddingBottom: '16px',
              borderBottom: '1px solid var(--neutral-light-gray)',
              marginBottom: '16px'
            }}>
              <div style={{ 
                width: '40px', 
                height: '40px', 
                background: 'var(--gradient-primary)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <Brain size={20} color="white" />
              </div>
              <div>
                <h2 style={{ 
                  fontSize: 'var(--font-size-lg)', 
                  fontWeight: 'var(--font-weight-semibold)',
                  marginBottom: '4px'
                }}>
                  Vybie - AI Assistant
                </h2>
                <p style={{ 
                  fontSize: 'var(--font-size-sm)', 
                  color: 'var(--neutral-gray)'
                }}>
                  Ask questions about your data
                </p>
              </div>
            </div>

            {/* Messages */}
            <div style={{ 
              flex: 1, 
              overflowY: 'auto',
              paddingRight: '8px'
            }}>
              {messages.map((message) => (
                <div key={message.id} style={{ marginBottom: '24px' }}>
                  <div style={{ 
                    display: 'flex', 
                    gap: '12px',
                    alignItems: message.type === 'user' ? 'flex-end' : 'flex-start'
                  }}>
                    {message.type === 'ai' && (
                      <div style={{ 
                        width: '32px', 
                        height: '32px', 
                        background: 'var(--gradient-primary)',
                        borderRadius: 'var(--border-radius-full)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                      }}>
                        <Brain size={16} color="white" />
                      </div>
                    )}
                    <div style={{ 
                      maxWidth: '70%',
                      background: message.type === 'user' ? 'var(--primary-purple)' : 'var(--neutral-white)',
                      color: message.type === 'user' ? 'white' : 'var(--neutral-dark)',
                      padding: '12px 16px',
                      borderRadius: message.type === 'user' ? 'var(--border-radius-lg) 0 var(--border-radius-lg) var(--border-radius-lg)' : '0 var(--border-radius-lg) var(--border-radius-lg) var(--border-radius-lg)',
                      border: message.type === 'ai' ? '1px solid var(--neutral-light-gray)' : 'none',
                      boxShadow: message.type === 'ai' ? 'var(--shadow-sm)' : 'none'
                    }}>
                      <p style={{ 
                        lineHeight: 'var(--line-height-relaxed)',
                        marginBottom: message.data ? '12px' : '0'
                      }}>
                        {message.content}
                      </p>
                      {message.data && renderVisualization(message.data, message.visualization)}
                    </div>
                    {message.type === 'user' && (
                      <div style={{ 
                        width: '32px', 
                        height: '32px', 
                        background: 'var(--gradient-vibrant)',
                        borderRadius: 'var(--border-radius-full)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                      }}>
                        <MessageSquare size={16} color="white" />
                      </div>
                    )}
                  </div>
                  <div style={{ 
                    fontSize: 'var(--font-size-xs)', 
                    color: 'var(--neutral-gray)',
                    marginTop: '8px',
                    marginLeft: message.type === 'user' ? 'auto' : '44px',
                    marginRight: message.type === 'user' ? '44px' : 'auto',
                    textAlign: message.type === 'user' ? 'right' : 'left'
                  }}>
                    {message.timestamp.toLocaleTimeString()}
                  </div>
                </div>
              ))}
              
              {isLoading && (
                <div style={{ marginBottom: '24px' }}>
                  <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                    <div style={{ 
                      width: '32px', 
                      height: '32px', 
                      background: 'var(--gradient-primary)',
                      borderRadius: 'var(--border-radius-full)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0
                    }}>
                      <Brain size={16} color="white" />
                    </div>
                    <div style={{ 
                      background: 'var(--neutral-white)',
                      padding: '12px 16px',
                      borderRadius: '0 var(--border-radius-lg) var(--border-radius-lg) var(--border-radius-lg)',
                      border: '1px solid var(--neutral-light-gray)',
                      boxShadow: 'var(--shadow-sm)'
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Loader2 size={16} className="animate-spin" />
                        <span style={{ fontSize: 'var(--font-size-sm)' }}>Analyzing your data...</span>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div style={{ 
              borderTop: '1px solid var(--neutral-light-gray)',
              paddingTop: '16px'
            }}>
              <div style={{ display: 'flex', gap: '12px' }}>
                <textarea
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="Ask a question about your data..."
                  style={{
                    flex: 1,
                    padding: '12px 16px',
                    border: '2px solid var(--neutral-light-gray)',
                    borderRadius: 'var(--border-radius-lg)',
                    fontFamily: 'var(--font-family-primary)',
                    fontSize: 'var(--font-size-base)',
                    resize: 'none',
                    minHeight: '48px',
                    maxHeight: '120px',
                    lineHeight: 'var(--line-height-normal)'
                  }}
                />
                <button
                  onClick={handleSendMessage}
                  disabled={!inputValue.trim() || isLoading}
                  className="btn btn-primary"
                  style={{ 
                    alignSelf: 'flex-end',
                    padding: '12px',
                    minWidth: '48px'
                  }}
                >
                  <Send size={20} />
                </button>
              </div>
            </div>
          </div>

          {/* Sidebar */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            {/* Database Selector */}
            <div className="card">
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '16px'
              }}>
                Connected Databases
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {databases.map((db) => (
                  <button
                    key={db.id}
                    onClick={() => setSelectedDatabase(db.id)}
                    style={{
                      padding: '12px',
                      border: selectedDatabase === db.id ? '2px solid var(--primary-purple)' : '1px solid var(--neutral-light-gray)',
                      borderRadius: 'var(--border-radius-lg)',
                      background: selectedDatabase === db.id ? 'var(--primary-purple)' : 'transparent',
                      color: selectedDatabase === db.id ? 'white' : 'var(--neutral-dark)',
                      textAlign: 'left',
                      cursor: 'pointer',
                      transition: 'all var(--transition-normal) var(--easing-default)'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                      <Database size={16} />
                      <span style={{ fontWeight: 'var(--font-weight-medium)' }}>{db.name}</span>
                    </div>
                    <div style={{ fontSize: 'var(--font-size-sm)', opacity: 0.8 }}>
                      {db.tables.length} tables • {db.type}
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* Quick Actions */}
            <div className="card">
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '16px'
              }}>
                Quick Actions
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <button className="btn btn-ghost" style={{ justifyContent: 'flex-start', padding: '8px 12px' }}>
                  <BarChart3 size={16} />
                  <span>Generate Report</span>
                </button>
                <button className="btn btn-ghost" style={{ justifyContent: 'flex-start', padding: '8px 12px' }}>
                  <Download size={16} />
                  <span>Export Data</span>
                </button>
                <button className="btn btn-ghost" style={{ justifyContent: 'flex-start', padding: '8px 12px' }}>
                  <Share2 size={16} />
                  <span>Share Insights</span>
                </button>
              </div>
            </div>

            {/* Recent Queries */}
            <div className="card">
              <h3 style={{ 
                fontSize: 'var(--font-size-lg)', 
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: '16px'
              }}>
                Recent Queries
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div style={{ 
                  padding: '8px 12px',
                  background: 'var(--neutral-light-gray)',
                  borderRadius: 'var(--border-radius-md)',
                  fontSize: 'var(--font-size-sm)'
                }}>
                  "Show me sales data"
                </div>
                <div style={{ 
                  padding: '8px 12px',
                  background: 'var(--neutral-light-gray)',
                  borderRadius: 'var(--border-radius-md)',
                  fontSize: 'var(--font-size-sm)'
                }}>
                  "How many users do we have?"
                </div>
                <div style={{ 
                  padding: '8px 12px',
                  background: 'var(--neutral-light-gray)',
                  borderRadius: 'var(--border-radius-md)',
                  fontSize: 'var(--font-size-sm)'
                }}>
                  "Product inventory status"
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ExplorePage; 