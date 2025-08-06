import { Link } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { 
  Database, 
  Brain, 
  Zap, 
  Shield, 
  BarChart3, 
  MessageSquare,
  ArrowRight,
  Play
} from 'lucide-react';

const HomePage = () => {
  const [currentHeadlineIndex, setCurrentHeadlineIndex] = useState(0);
  const [currentText, setCurrentText] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);

  const headlines = [
    "Explore Your Database with Natural Language",
    "Ask Questions in Plain English",
    "Get Instant AI-Powered Insights",
    "Connect Multiple Databases Seamlessly",
    "Transform Your Data Experience"
  ];

  useEffect(() => {
    const typeSpeed = isDeleting ? 30 : 60;
    const deleteSpeed = 30;
    const pauseTime = 1500;

    const typeWriter = () => {
      const currentHeadline = headlines[currentHeadlineIndex];
      
      if (isDeleting) {
        // Deleting text
        if (currentText.length > 0) {
          setTimeout(() => {
            setCurrentText(currentText.slice(0, -1));
          }, deleteSpeed);
        } else {
          setIsDeleting(false);
          setCurrentHeadlineIndex((prev) => (prev + 1) % headlines.length);
        }
      } else {
        // Typing text
        if (currentText.length < currentHeadline.length) {
          setTimeout(() => {
            setCurrentText(currentHeadline.slice(0, currentText.length + 1));
          }, typeSpeed);
        } else {
          // Pause before deleting
          setTimeout(() => {
            setIsDeleting(true);
          }, pauseTime);
        }
      }
    };

    const timer = setTimeout(typeWriter, isDeleting ? deleteSpeed : typeSpeed);
    return () => clearTimeout(timer);
  }, [currentText, currentHeadlineIndex, isDeleting, headlines]);

  return (
    <div className="min-h-screen">
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
            <li><Link to="/explore" className="nav-menu-item">Explore</Link></li>
            <li><Link to="/login" className="nav-menu-item">Login</Link></li>
            <li><Link to="/signup" className="btn btn-primary">Get Started</Link></li>
          </ul>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="section">
        <div className="hero fade-in">
          <h1 className="hero-headline" style={{ 
            minHeight: '120px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <span style={{ 
              background: 'var(--gradient-vibrant)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              backgroundClip: 'text',
              borderRight: '3px solid var(--primary-purple)',
              paddingRight: '8px',
              animation: 'blink 1s infinite'
            }}>
              {currentText}
            </span>
          </h1>
          <p className="hero-subheadline">
            No-code, AI-powered tool to connect your databases and explore using natural language. Get instant insights, 
            visualizations, and comprehensive analysis powered by LLM and advanced AI agents.
          </p>
          <div style={{ display: 'flex', gap: '16px', justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link to="/signup" className="btn btn-primary">
              Start Free Trial
              <ArrowRight size={20} />
            </Link>
            <button className="btn btn-secondary">
              <Play size={20} />
              Watch Demo
            </button>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="section" style={{ background: 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)' }}>
        <div className="container">
          <div style={{ textAlign: 'center', marginBottom: '64px' }}>
            <h2 style={{ 
              fontSize: 'var(--font-size-4xl)', 
              fontWeight: 'var(--font-weight-bold)',
              marginBottom: '16px'
            }}>
              Powerful Features for Modern Teams
            </h2>
            <p style={{ 
              fontSize: 'var(--font-size-lg)', 
              color: 'var(--neutral-gray)',
              maxWidth: '600px',
              margin: '0 auto'
            }}>
              Everything you need to understand and explore your data with AI-powered insights
            </p>
          </div>

          <div className="feature-grid">
            <div className="card translate-hover">
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-primary)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '16px'
              }}>
                <Database size={24} color="white" />
              </div>
              <h3 className="feature-title">Multi-Database Support</h3>
              <p className="feature-description">
                Connect MySQL, PostgreSQL, MongoDB, and more. Seamlessly switch between 
                databases and get unified insights across all your data sources.
              </p>
            </div>

            <div className="card translate-hover">
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-blue)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '16px'
              }}>
                <Brain size={24} color="white" />
              </div>
              <h3 className="feature-title">AI-Powered Analysis</h3>
              <p className="feature-description">
                Ask questions in natural language and get intelligent responses. Our AI 
                understands your data structure and provides contextual insights.
              </p>
            </div>

            <div className="card translate-hover">
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-pink)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '16px'
              }}>
                <BarChart3 size={24} color="white" />
              </div>
              <h3 className="feature-title">Interactive Visualizations</h3>
              <p className="feature-description">
                Get beautiful charts and graphs automatically generated from your queries. 
                Export and share insights with your team effortlessly.
              </p>
            </div>

            <div className="card translate-hover">
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-vibrant)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '16px'
              }}>
                <Zap size={24} color="white" />
              </div>
              <h3 className="feature-title">Real-time Processing</h3>
              <p className="feature-description">
                Get instant results with our optimized query engine. No more waiting for 
                complex queries to complete.
              </p>
            </div>

            <div className="card translate-hover">
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-primary)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '16px'
              }}>
                <Shield size={24} color="white" />
              </div>
              <h3 className="feature-title">Enterprise Security</h3>
              <p className="feature-description">
                Bank-level security with encrypted connections, role-based access control, 
                and comprehensive audit logs for compliance.
              </p>
            </div>

            <div className="card translate-hover">
              <div style={{ 
                width: '48px', 
                height: '48px', 
                background: 'var(--gradient-blue)',
                borderRadius: 'var(--border-radius-lg)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '16px'
              }}>
                <MessageSquare size={24} color="white" />
              </div>
              <h3 className="feature-title">Collaborative Workspace</h3>
              <p className="feature-description">
                Share queries, insights, and dashboards with your team. Comment, 
                collaborate, and build knowledge together.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="section">
        <div className="container">
          <div className="card-feature" style={{ textAlign: 'center' }}>
            <h2 style={{ 
              fontSize: 'var(--font-size-3xl)', 
              fontWeight: 'var(--font-weight-bold)',
              marginBottom: '16px'
            }}>
              Ready to Transform Your Data Experience?
            </h2>
            <p style={{ 
              fontSize: 'var(--font-size-lg)', 
              marginBottom: '32px',
              opacity: 0.9
            }}>
              Join thousands of teams using dbVybe to unlock the full potential of their data
            </p>
            <Link to="/signup" className="btn btn-secondary" style={{ background: 'white', color: 'var(--primary-purple)' }}>
              Start Your Free Trial
              <ArrowRight size={20} />
            </Link>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer style={{ 
        background: 'var(--neutral-dark)', 
        color: 'white', 
        padding: '48px 0',
        marginTop: '80px'
      }}>
        <div className="container">
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))',
            gap: '32px'
          }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
                <Database size={24} />
                <span style={{ fontWeight: 'var(--font-weight-bold)', fontSize: 'var(--font-size-lg)' }}>
                  dbVybe
                </span>
              </div>
              <p style={{ color: '#94a3b8', lineHeight: 'var(--line-height-relaxed)' }}>
                The intelligent way to explore and understand your databases with natural language.
              </p>
            </div>
            
            <div>
              <h4 style={{ marginBottom: '16px', fontWeight: 'var(--font-weight-semibold)' }}>Product</h4>
              <ul style={{ listStyle: 'none', color: '#94a3b8' }}>
                <li style={{ marginBottom: '8px' }}><Link to="/dashboard" style={{ color: 'inherit', textDecoration: 'none' }}>Dashboard</Link></li>
                <li style={{ marginBottom: '8px' }}><Link to="/explore" style={{ color: 'inherit', textDecoration: 'none' }}>Explore</Link></li>
                <li style={{ marginBottom: '8px' }}>Features</li>
                <li style={{ marginBottom: '8px' }}>Pricing</li>
              </ul>
            </div>
            
            <div>
              <h4 style={{ marginBottom: '16px', fontWeight: 'var(--font-weight-semibold)' }}>Company</h4>
              <ul style={{ listStyle: 'none', color: '#94a3b8' }}>
                <li style={{ marginBottom: '8px' }}>About</li>
                <li style={{ marginBottom: '8px' }}>Blog</li>
                <li style={{ marginBottom: '8px' }}>Careers</li>
                <li style={{ marginBottom: '8px' }}>Contact</li>
              </ul>
            </div>
            
            <div>
              <h4 style={{ marginBottom: '16px', fontWeight: 'var(--font-weight-semibold)' }}>Support</h4>
              <ul style={{ listStyle: 'none', color: '#94a3b8' }}>
                <li style={{ marginBottom: '8px' }}>Documentation</li>
                <li style={{ marginBottom: '8px' }}>Help Center</li>
                <li style={{ marginBottom: '8px' }}>API Reference</li>
                <li style={{ marginBottom: '8px' }}>Status</li>
              </ul>
            </div>
          </div>
          
          <div style={{ 
            borderTop: '1px solid #334155', 
            marginTop: '48px', 
            paddingTop: '24px',
            textAlign: 'center',
            color: '#94a3b8'
          }}>
            <p>&copy; 2025 dbVybe. All rights reserved.</p>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default HomePage; 