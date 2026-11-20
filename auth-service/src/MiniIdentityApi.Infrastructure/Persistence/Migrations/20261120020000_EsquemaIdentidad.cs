using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MiniIdentityApi.Infrastructure.Persistence.Migrations
{
    /// <inheritdoc />
    public partial class EsquemaIdentidad : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.EnsureSchema(
                name: "identidad");

            migrationBuilder.RenameTable(
                name: "usuarios_roles",
                newName: "usuarios_roles",
                newSchema: "identidad");

            migrationBuilder.RenameTable(
                name: "usuarios",
                newName: "usuarios",
                newSchema: "identidad");

            migrationBuilder.RenameTable(
                name: "roles_permisos",
                newName: "roles_permisos",
                newSchema: "identidad");

            migrationBuilder.RenameTable(
                name: "roles",
                newName: "roles",
                newSchema: "identidad");

            migrationBuilder.RenameTable(
                name: "permisos",
                newName: "permisos",
                newSchema: "identidad");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.RenameTable(
                name: "usuarios_roles",
                schema: "identidad",
                newName: "usuarios_roles");

            migrationBuilder.RenameTable(
                name: "usuarios",
                schema: "identidad",
                newName: "usuarios");

            migrationBuilder.RenameTable(
                name: "roles_permisos",
                schema: "identidad",
                newName: "roles_permisos");

            migrationBuilder.RenameTable(
                name: "roles",
                schema: "identidad",
                newName: "roles");

            migrationBuilder.RenameTable(
                name: "permisos",
                schema: "identidad",
                newName: "permisos");
        }
    }
}
