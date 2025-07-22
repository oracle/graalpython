require 'cgi'
require 'kramdown'
require 'kramdown-parser-gfm'

module GraalPy
  class PythonOptions < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text.strip
    end

    def render(context)
      content = File.read(@text)
      options = {}

      regexp = /
        ^(\s*@EngineOption\s*)?                                  # Optional @EngineOption
        \s*@Option\s*\(                                          # @Option(
        (.*?)                                                    # non-greedy match content
        \)\s*(?:\/\/)?\s*                                        # )
        public\s+static\s+final\s+OptionKey<[^>]+>\s+(\w+)       # field name (e.g. InitialLocale)
      /mx

      help_regexp = /
          help\s*=\s*((?:"[^"]*"\s*(?:\+\s*"[^"]*"\s*)*))        # help = "..."+ "...",
      /mx

      stability_regexp = /stability\s*=\s*OptionStability\.(\w+)/

      category_regexp = /category\s*=\s*OptionCategory\.(\w+)/

      usage_regexp = /usageSyntax\s*=\s*((?:"[^"]*"\s*(?:\+\s*"[^"]*"\s*)*))/

      content.scan(regexp) do |engine_option, content, field|
        engine_option = !!engine_option

        help = content.match(help_regexp)
        help = help[1].scan(/"([^"]*)"/).flatten.join(" ").gsub(/\s+/, " ").strip if help

        stable = content.match(stability_regexp)
        stable = stable[1] == "STABLE" if stable

        category = content.match(category_regexp)
        category = category[1] if category

        usage = content.match(usage_regexp)
        usage = usage[1].scan(/"([^"]*)"/).flatten.join(" ").gsub(/\s+/, " ").strip if usage

        if help && !help.empty?
          help << "." unless help.end_with? "."
          help = CGI::escapeHTML help
          help << " Accepts: <code>#{CGI::escapeHTML usage}</code>" if usage and !usage.empty?
          options[field] = { help: help, category: category, stable: stable, engine_option: engine_option }
        end
      end

      doc_content = options.select do |name, opt|
        opt[:stable] && ["USER", "EXPERT"].include?(opt[:category])
      end.sort_by do |name, opt|
        [opt[:category] == "USER" ? 0 : 1, name.downcase]
      end.map do |name, opt|
        <<~HTML
        <p>
          <strong>#{name}</strong>
          #{' (Has to be the same for all Contexts in an Engine)' if opt[:engine_option]}
        </p>
        <p>
        &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;#{opt[:help]}
        </p>
        HTML
      end.join("\n")
    end
  end
end

Liquid::Template.register_tag('python_options', GraalPy::PythonOptions)
