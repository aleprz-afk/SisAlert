using Microsoft.EntityFrameworkCore;
using MiniIdentityApi.Domain.Entities;

namespace MiniIdentityApi.Infrastructure.Persistence;

/// <summary>
/// Contexto de Entity Framework Core para usuarios, roles y permisos.
/// Usa las mismas credenciales de PostgreSQL que el resto del proyecto, pero
/// tablas propias (usuarios, roles, permisos, usuarios_roles, roles_permisos).
/// </summary>
public class IdentityDbContext : DbContext
{
    /// <summary>Esquema de PostgreSQL donde viven las tablas de identidad.</summary>
    public const string Esquema = "identidad";

    /// <summary>
    /// Esquema por defecto de la tabla de historial de migraciones: "public", para que las
    /// bases creadas antes de mover las tablas reconozcan las migraciones ya aplicadas.
    /// En bases nuevas con usuarios por servicio se cambia con Persistencia__EsquemaHistorial.
    /// </summary>
    public const string EsquemaHistorial = "public";

    public IdentityDbContext(DbContextOptions<IdentityDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();
    public DbSet<Role> Roles => Set<Role>();
    public DbSet<Permission> Permissions => Set<Permission>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        // Esquema propio dentro de la base compartida; alertas-service usa el esquema "alertas".
        modelBuilder.HasDefaultSchema(Esquema);

        modelBuilder.Entity<User>(b =>
        {
            b.ToTable("usuarios");
            b.HasKey(u => u.Id);
            b.Property(u => u.Id).HasColumnName("id");
            b.Property(u => u.Username).HasColumnName("username").HasMaxLength(100).IsRequired();
            b.Property(u => u.Email).HasColumnName("email").HasMaxLength(200).IsRequired();
            b.Property(u => u.Status).HasColumnName("status").HasConversion<int>().IsRequired();
            b.HasIndex(u => u.Username).IsUnique();
            b.HasIndex(u => u.Email).IsUnique();

            b.OwnsOne(u => u.Credential, c =>
            {
                c.Property(x => x.PasswordHash).HasColumnName("password_hash").HasMaxLength(200).IsRequired();
                c.Property(x => x.Salt).HasColumnName("salt").HasMaxLength(200).IsRequired();
                c.Property(x => x.LastChangedAt).HasColumnName("last_changed_at").IsRequired();
            });
            b.Navigation(u => u.Credential).IsRequired();

            b.HasMany(u => u.Roles)
                .WithMany()
                .UsingEntity<Dictionary<string, object>>(
                    "usuarios_roles",
                    right => right.HasOne<Role>().WithMany().HasForeignKey("rol_id"),
                    left => left.HasOne<User>().WithMany().HasForeignKey("usuario_id"),
                    join => join.HasKey("usuario_id", "rol_id"));
        });

        modelBuilder.Entity<Role>(b =>
        {
            b.ToTable("roles");
            b.HasKey(r => r.Id);
            b.Property(r => r.Id).HasColumnName("id");
            b.Property(r => r.Name).HasColumnName("name").HasMaxLength(100).IsRequired();
            b.HasIndex(r => r.Name).IsUnique();

            b.HasMany(r => r.Permissions)
                .WithMany()
                .UsingEntity<Dictionary<string, object>>(
                    "roles_permisos",
                    right => right.HasOne<Permission>().WithMany().HasForeignKey("permiso_id"),
                    left => left.HasOne<Role>().WithMany().HasForeignKey("rol_id"),
                    join => join.HasKey("rol_id", "permiso_id"));
        });

        modelBuilder.Entity<Permission>(b =>
        {
            b.ToTable("permisos");
            b.HasKey(p => p.Id);
            b.Property(p => p.Id).HasColumnName("id");
            b.Property(p => p.Code).HasColumnName("code").HasMaxLength(100).IsRequired();
            b.Property(p => p.Description).HasColumnName("description").HasMaxLength(200).IsRequired();
            b.HasIndex(p => p.Code).IsUnique();
        });
    }
}
